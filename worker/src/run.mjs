// Entry point for the GitHub Actions job. For every series someone watches:
// ask MangaDex for the newest chapter, compare with what we announced last, send a push if new.
//
//   node src/run.mjs                 real run (needs FIREBASE_SERVICE_ACCOUNT)
//   node src/run.mjs --dry-run       reads Firestore, prints decisions, sends and writes nothing
//   node src/run.mjs --series ID,ID  just print the latest chapter of those series (no Firebase)
import admin from 'firebase-admin';
import { decide, buildMessage, pickLatest } from './logic.mjs';
import { fetchFeed, sleep } from './mangadex.mjs';

const args = process.argv.slice(2);
const dryRun = args.includes('--dry-run') || process.env.DRY_RUN === '1';
const seriesArg = args.includes('--series') ? args[args.indexOf('--series') + 1] : null;

async function previewSeries(ids) {
  for (const id of ids.split(',').map((s) => s.trim()).filter(Boolean)) {
    const latest = pickLatest(await fetchFeed(id));
    console.log(id, latest ? `latest chapter ${latest.number} (${latest.id})` : 'no readable chapters');
    await sleep(300);
  }
}

function initFirebase() {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (!raw) throw new Error('FIREBASE_SERVICE_ACCOUNT is not set. Add it as a repository secret.');
  admin.initializeApp({ credential: admin.credential.cert(JSON.parse(raw)) });
  return { db: admin.firestore(), messaging: admin.messaging() };
}

async function main() {
  if (seriesArg) return previewSeries(seriesArg);

  const { db, messaging } = initFirebase();
  const snap = await db.collection('watched').where('subscribers', '>', 0).get();
  console.log(`${snap.size} watched series${dryRun ? ' (dry run)' : ''}`);

  let sent = 0, baselined = 0, failed = 0;
  for (const doc of snap.docs) {
    const state = doc.data();
    try {
      const latest = pickLatest(await fetchFeed(doc.id));
      const action = decide(latest, state);
      console.log(`${doc.id} "${state.title}": ${action}${latest ? ` (ch ${latest.number})` : ''}`);
      if (action === 'none' || dryRun) continue;

      if (action === 'notify') {
        await messaging.send(buildMessage(doc.id, state.title, latest));
        sent++;
      } else {
        baselined++;
      }
      await doc.ref.update({
        lastChapterId: latest.id,
        lastChapterNumber: String(latest.number),
        lastCheckedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
    } catch (err) {
      failed++;
      console.error(`${doc.id}: ${err.message}`);
    }
    await sleep(300);
  }
  console.log(`done: ${sent} alerts sent, ${baselined} baselined, ${failed} failed`);
  // Fail the job only when everything failed, so one bad series never hides the others.
  if (snap.size > 0 && failed === snap.size) process.exit(1);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
