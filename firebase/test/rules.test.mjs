// Run with: npm test (starts the Firestore emulator, runs these, stops it).
import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} from '@firebase/rules-unit-testing';
import {
  doc, setDoc, getDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, increment,
} from 'firebase/firestore';

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-bingehwa',
    firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
  });
});

after(async () => { await env.cleanup(); });
beforeEach(async () => { await env.clearFirestore(); });

const alice = () => env.authenticatedContext('alice').firestore();
const bob = () => env.authenticatedContext('bob').firestore();
const anon = () => env.unauthenticatedContext().firestore();

const comment = (uid, over = {}) => ({
  scope: 's:abc', seriesId: 'abc', chapterId: '', parentId: '', uid, name: 'Alice',
  text: 'hello', spoiler: false, likeCount: 0, replyCount: 0, createdAt: serverTimestamp(), ...over,
});

test('signed-out users cannot read comments', async () => {
  await assertFails(getDoc(doc(anon(), 'comments/c1')));
});

test('a user can post a valid comment as themselves', async () => {
  await assertSucceeds(setDoc(doc(alice(), 'comments/c1'), comment('alice')));
});

test('a user cannot post as someone else', async () => {
  await assertFails(setDoc(doc(alice(), 'comments/c1'), comment('bob')));
});

test('comments over 1000 characters are rejected', async () => {
  await assertFails(setDoc(doc(alice(), 'comments/c1'), comment('alice', { text: 'x'.repeat(1001) })));
});

test('a new comment cannot start with likes', async () => {
  await assertFails(setDoc(doc(alice(), 'comments/c1'), comment('alice', { likeCount: 50 })));
});

test('only the author can delete a comment', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'comments/c1'), { ...comment('alice'), createdAt: new Date() });
  });
  await assertFails(deleteDoc(doc(bob(), 'comments/c1')));
  await assertSucceeds(deleteDoc(doc(alice(), 'comments/c1')));
});

async function seedComment() {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'comments/c1'), { ...comment('alice'), createdAt: new Date() });
  });
}

test('liking adds exactly one like and one like document', async () => {
  await seedComment();
  const db = bob();
  const batch = writeBatch(db);
  batch.set(doc(db, 'comments/c1/likes/bob'), { at: serverTimestamp() });
  batch.update(doc(db, 'comments/c1'), { likeCount: increment(1) });
  await assertSucceeds(batch.commit());
});

test('a like cannot jump by more than one', async () => {
  await seedComment();
  const db = bob();
  const batch = writeBatch(db);
  batch.set(doc(db, 'comments/c1/likes/bob'), { at: serverTimestamp() });
  batch.update(doc(db, 'comments/c1'), { likeCount: increment(5) });
  await assertFails(batch.commit());
});

test('bumping likes without a like document is rejected', async () => {
  await seedComment();
  await assertFails(updateDoc(doc(bob(), 'comments/c1'), { likeCount: increment(1) }));
});

test('other fields of a comment cannot be edited by others', async () => {
  await seedComment();
  await assertFails(updateDoc(doc(bob(), 'comments/c1'), { text: 'hacked' }));
});

test('reply count can rise by one', async () => {
  await seedComment();
  await assertSucceeds(updateDoc(doc(bob(), 'comments/c1'), { replyCount: increment(1) }));
  await assertFails(updateDoc(doc(bob(), 'comments/c1'), { replyCount: increment(3) }));
});

test('rating: first rating creates the series totals together with the rating', async () => {
  const db = alice();
  const batch = writeBatch(db);
  batch.set(doc(db, 'series/abc/ratings/alice'), {
    uid: 'alice', name: 'Alice', stars: 4, tags: ['Great art'], review: 'good', public: true, updatedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'series/abc'), { ratingCount: 1, ratingSum: 4 });
  await assertSucceeds(batch.commit());
});

test('rating: totals cannot be inflated', async () => {
  const db = alice();
  const batch = writeBatch(db);
  batch.set(doc(db, 'series/abc/ratings/alice'), {
    uid: 'alice', name: 'Alice', stars: 5, tags: [], review: '', public: true, updatedAt: serverTimestamp(),
  });
  batch.set(doc(db, 'series/abc'), { ratingCount: 1000, ratingSum: 5000 });
  await assertFails(batch.commit());
});

test('rating: stars must be 1 to 5', async () => {
  await assertFails(setDoc(doc(alice(), 'series/abc/ratings/alice'), {
    uid: 'alice', name: 'Alice', stars: 6, tags: [], review: '', public: true, updatedAt: serverTimestamp(),
  }));
});

test('rating: nobody writes another user\'s rating', async () => {
  await assertFails(setDoc(doc(alice(), 'series/abc/ratings/bob'), {
    uid: 'bob', name: 'Bob', stars: 1, tags: [], review: '', public: true, updatedAt: serverTimestamp(),
  }));
});

test('private ratings are only readable by their owner', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'series/abc/ratings/alice'), {
      uid: 'alice', name: 'Alice', stars: 3, tags: [], review: 'secret', public: false, updatedAt: new Date(),
    });
  });
  await assertFails(getDoc(doc(bob(), 'series/abc/ratings/alice')));
  await assertSucceeds(getDoc(doc(alice(), 'series/abc/ratings/alice')));
});

test('watched: subscribing creates the doc once with one subscriber', async () => {
  await assertSucceeds(setDoc(doc(alice(), 'watched/abc'), { title: 'Tower', subscribers: 1 }));
});

test('watched: subscriber count moves by one at a time', async () => {
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), 'watched/abc'), { title: 'Tower', subscribers: 2, lastChapterId: 'x' });
  });
  await assertSucceeds(updateDoc(doc(bob(), 'watched/abc'), { subscribers: 3 }));
  await assertFails(updateDoc(doc(bob(), 'watched/abc'), { subscribers: 50 }));
  await assertFails(updateDoc(doc(bob(), 'watched/abc'), { lastChapterId: 'forged' }));
});

test('reports can be created once per user and comment, and never read back', async () => {
  await assertSucceeds(setDoc(doc(bob(), 'reports/c1_bob'), { commentId: 'c1', uid: 'bob', reason: 'spam', createdAt: serverTimestamp() }));
  await assertFails(getDoc(doc(bob(), 'reports/c1_bob')));
  await assertFails(setDoc(doc(bob(), 'reports/c1_alice'), { commentId: 'c1', uid: 'bob', reason: 'spam', createdAt: serverTimestamp() }));
});

test('unknown collections are closed', async () => {
  await assertFails(setDoc(doc(alice(), 'random/x'), { a: 1 }));
  await assertFails(getDoc(doc(alice(), 'random/x')));
});
