import { readFileSync, readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'

const dist = resolve(import.meta.dirname, '..', 'web', 'dist')
const files = []
const collect = (directory) => {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) collect(path)
    else if (/\.(html|css|js)$/i.test(entry.name)) files.push(path)
  }
}
collect(dist)
const checks = [
  /\b(?:src|href)\s*=\s*["']\s*(?:https?:\/\/|\/\/cdn\.)/i,
  /url\(\s*["']?\s*(?:https?:\/\/|\/\/cdn\.)/i,
  /(?:fetch|EventSource|WebSocket|import)\(\s*["']\s*(?:https?:\/\/|\/\/cdn\.)/i,
]
const violations = []
for (const file of files) {
  const content = readFileSync(file, 'utf8')
  if (checks.some((check) => check.test(content))) violations.push(file)
}
if (violations.length > 0) {
  console.error('External runtime resource references found:', violations)
  process.exit(1)
}
console.log(`Offline resource scan passed for ${files.length} files.`)
