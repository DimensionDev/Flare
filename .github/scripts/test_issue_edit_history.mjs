import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

const workflow = await readFile(new URL('../workflows/issue-edit-history.yml', import.meta.url), 'utf8');
const marker = '          script: |\n';
const scriptStart = workflow.indexOf(marker);
assert.notEqual(scriptStart, -1);
const script = workflow.slice(scriptStart + marker.length).replace(/^ {12}/gm, '');
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;
const action = new AsyncFunction('github', 'context', script);

function createHarness() {
  const issues = [];
  const calls = [];
  const api = {
    listForRepo: async () => ({ data: issues }),
    create: async input => {
      const data = { ...input, number: 100 + issues.length };
      issues.unshift(data);
      calls.push(['create', input]);
      return { data };
    },
    update: async input => {
      const issue = issues.find(candidate => candidate.number === input.issue_number);
      Object.assign(issue, input);
      calls.push(['update', input]);
      return { data: issue };
    },
    lock: async input => calls.push(['lock', input]),
  };
  const github = {
    paginate: async (method, input) => (await method(input)).data,
    rest: { issues: api },
  };
  return { github, issues, calls };
}

async function verifySplitEdits(order) {
  const { github, issues, calls } = createHarness();
  const issue = {
    id: 1,
    number: 2444,
    html_url: 'https://github.com/DimensionDev/Flare/issues/2444',
    title: '.',
    body: '.',
  };
  const events = {
    title: { issue, changes: { title: { from: 'Original title' } } },
    body: { issue, changes: { body: { from: 'Original body' } } },
  };

  for (const event of order) {
    await action(github, {
      repo: { owner: 'DimensionDev', repo: 'Flare' },
      payload: events[event],
    });
  }

  assert.equal(calls.filter(([name]) => name === 'create').length, 1);
  assert.equal(issues.length, 1);
  assert.equal(issues[0].title, 'Original title');
  assert.match(issues[0].body, /Original body/);
  assert.match(issues[0].body, /issues\/2444/);
  assert(calls.some(([name, input]) => name === 'lock' && input.issue_number === 2444));
}

await verifySplitEdits(['title', 'body']);
await verifySplitEdits(['body', 'title']);

const ignored = createHarness();
await action(ignored.github, {
  repo: { owner: 'DimensionDev', repo: 'Flare' },
  payload: {
    issue: { id: 2, number: 2, title: 'Updated title', body: 'Updated body' },
    changes: { title: { from: 'Original title' }, body: { from: 'Original body' } },
  },
});
assert.equal(ignored.calls.length, 0);

console.log('Issue restoration checks passed');
