import { cpSync, mkdirSync, readdirSync, rmSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const root = resolve(import.meta.dirname, '..')
const run = (command, args, cwd = root) => {
  const executable = process.platform === 'win32' && command === 'npm' ? 'npm.cmd' : command
  const result = spawnSync(executable, args, {
    cwd,
    stdio: 'inherit',
    shell: process.platform === 'win32' && command === 'npm',
  })
  if (result.error) throw result.error
  if (result.status !== 0) throw new Error(`${command} exited with ${result.status}`)
}

const web = join(root, 'web')
const output = join(root, 'build-out')
const classes = join(output, 'classes')
const stage = join(output, 'stage')
const jar = join(output, 'droidscope.jar')
rmSync(classes, { recursive: true, force: true })
rmSync(stage, { recursive: true, force: true })
mkdirSync(classes, { recursive: true })
mkdirSync(stage, { recursive: true })

run('npm', ['ci'], web)
run('npm', ['test', '--', '--run'], web)
run('npm', ['run', 'build'], web)

const javaSources = []
const collect = (directory) => {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) collect(path)
    else if (entry.name.endsWith('.java')) javaSources.push(path)
  }
}
collect(join(root, 'windows', 'src', 'main', 'java'))
run('javac', ['-encoding', 'UTF-8', '-d', classes, ...javaSources])
cpSync(join(web, 'dist'), join(stage, 'web'), { recursive: true })
cpSync(classes, stage, { recursive: true })
run('jar', ['--create', '--file', jar, '-C', stage, '.'])
console.log(`Local Core JAR created: ${jar}`)
