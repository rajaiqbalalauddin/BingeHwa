import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decide, buildMessage, pickLatest, numberValue } from '../src/logic.mjs';

const ch = (id, n, pages = 10, publishAt = '2026-01-01T00:00:00+00:00', externalUrl = null) => ({
  id, attributes: { chapter: n, pages, publishAt, externalUrl },
});

test('numberValue parses decimals and rejects words', () => {
  assert.equal(numberValue('12.5'), 12.5);
  assert.equal(numberValue('Oneshot'), null);
  assert.equal(numberValue(null), null);
});

test('pickLatest chooses the highest chapter number, not the first one', () => {
  const latest = pickLatest([ch('a', '9'), ch('b', '10'), ch('c', '2')]);
  assert.equal(latest.id, 'b');
});

test('pickLatest skips chapters that cannot be opened', () => {
  const latest = pickLatest([ch('a', '11', 0), ch('b', '10', 12)]);
  assert.equal(latest.id, 'b');
});

test('pickLatest keeps external-only chapters (official links)', () => {
  const latest = pickLatest([ch('a', '11', 0, '2026-01-01T00:00:00+00:00', 'https://publisher.example')]);
  assert.equal(latest.id, 'a');
});

test('pickLatest breaks ties by publish time', () => {
  const latest = pickLatest([ch('old', '5', 10, '2026-01-01T00:00:00+00:00'), ch('new', '5', 10, '2026-02-01T00:00:00+00:00')]);
  assert.equal(latest.id, 'new');
});

test('pickLatest returns null for empty feeds', () => {
  assert.equal(pickLatest([]), null);
  assert.equal(pickLatest(undefined), null);
});

test('first sighting is a baseline, never an alert', () => {
  assert.equal(decide({ id: 'a', number: '10' }, { title: 'x', subscribers: 1 }), 'baseline');
});

test('same chapter again is quiet', () => {
  assert.equal(decide({ id: 'a', number: '10' }, { lastChapterId: 'a', lastChapterNumber: '10' }), 'none');
});

test('a higher chapter number notifies', () => {
  assert.equal(decide({ id: 'b', number: '11' }, { lastChapterId: 'a', lastChapterNumber: '10' }), 'notify');
});

test('a re-upload of an announced chapter does not notify again', () => {
  assert.equal(decide({ id: 'b', number: '10' }, { lastChapterId: 'a', lastChapterNumber: '10' }), 'none');
});

test('half chapters count as new', () => {
  assert.equal(decide({ id: 'b', number: '10.5' }, { lastChapterId: 'a', lastChapterNumber: '10' }), 'notify');
});

test('no latest chapter means nothing to do', () => {
  assert.equal(decide(null, { lastChapterId: 'a' }), 'none');
});

test('message goes to the series topic with string-only data', () => {
  const m = buildMessage('abc-123', 'Tower of Quiet Rain', { id: 'ch1', number: 86 });
  assert.equal(m.topic, 'series_abc-123');
  for (const v of Object.values(m.data)) assert.equal(typeof v, 'string');
  assert.equal(m.data.number, '86');
  assert.equal(m.data.chapterId, 'ch1');
});
