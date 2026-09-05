export function readSessionToken(location: Location, storage: Storage): string {
  const params = new URLSearchParams(location.hash.replace(/^#/, ''))
  const fromHash = params.get('token')
  if (fromHash) {
    storage.setItem('droidscope.session', fromHash)
    history.replaceState(null, '', `${location.pathname}${location.search}`)
    return fromHash
  }
  return storage.getItem('droidscope.session') ?? ''
}
