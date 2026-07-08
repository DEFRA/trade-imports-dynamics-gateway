# Pipeline diagrams

Source (`*.mmd`) and rendered (`*.svg`) diagrams for
[`../notification-pipeline.md`](../notification-pipeline.md).

The SVGs are committed so the doc displays in any viewer (editor preview,
GitHub, Confluence) without a Mermaid plugin. If you edit a `.mmd`, re-render it.

## Regenerate

Requires Node. Uses [`@mermaid-js/mermaid-cli`](https://github.com/mermaid-js/mermaid-cli)
(downloads a headless Chromium on first run). `.puppeteer.json` passes
`--no-sandbox` for restricted/CI environments.

```bash
cd docs/diagrams
for f in *.mmd; do
  npx -y @mermaid-js/mermaid-cli -i "$f" -o "${f%.mmd}.svg" -p .puppeteer.json -b transparent
done
```

To render a single diagram:

```bash
npx -y @mermaid-js/mermaid-cli -i 03-error-handling.mmd -o 03-error-handling.svg -p .puppeteer.json -b transparent
```
