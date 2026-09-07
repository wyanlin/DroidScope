import { cpSync, existsSync, mkdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { spawnSync } from 'node:child_process'

const root = resolve(import.meta.dirname, '..')
const jar = join(root, 'build-out', 'droidscope.jar')
if (!existsSync(jar)) throw new Error('build-out/droidscope.jar is missing; run build-local-core.mjs first')
const javaHome = process.env.JAVA_HOME
const jpackage = javaHome
  ? join(javaHome, 'bin', process.platform === 'win32' ? 'jpackage.exe' : 'jpackage')
  : process.platform === 'win32' ? 'jpackage.exe' : 'jpackage'
const input = join(root, 'build-out', 'package-input')
const output = join(root, 'build-out', `DroidScope-${process.platform}`)
mkdirSync(input, { recursive: true })
cpSync(jar, join(input, 'droidscope.jar'))
const result = spawnSync(jpackage, ['--type', 'app-image', '--name', 'DroidScope', '--dest', output, '--input', input, '--main-jar', 'droidscope.jar', '--main-class', 'com.droidscope.Main', '--java-options', '-Dfile.encoding=UTF-8'], { cwd: root, stdio: 'inherit' })
if (result.error) throw result.error
if (result.status !== 0) throw new Error(`jpackage exited with ${result.status}`)
console.log(`Desktop application created for ${process.platform}: ${output}`)
