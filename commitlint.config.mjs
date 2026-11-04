// GajaMitra commit rules: "<type>: <message>", no scope, no AI attribution.
// Keep TYPES in sync with CLAUDE.md, .releaserc.json, .github/workflows/pr-checks.yml and .claude/hooks/guard.py.
const TYPES = ['feat', 'fix', 'perf', 'refactor', 'docs', 'style', 'test', 'build', 'ci', 'chore', 'revert'];

export default {
  extends: ['@commitlint/config-conventional'],
  plugins: [
    {
      rules: {
        'no-ai-attribution': ({ raw }) => [
          !/co-authored-by\s*:|generated with \[?claude code/i.test(raw ?? ''),
          'commits must not contain AI co-author or attribution lines',
        ],
      },
    },
  ],
  rules: {
    'type-enum': [2, 'always', TYPES],
    'scope-empty': [2, 'always'],
    'subject-case': [0],
    'header-max-length': [2, 'always', 100],
    'body-max-line-length': [0],
    'footer-max-line-length': [0],
    'no-ai-attribution': [2, 'always'],
  },
};
