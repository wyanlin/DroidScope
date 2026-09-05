import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { HttpDroidScopeClient } from './local-client/HttpDroidScopeClient'
import { readSessionToken } from './local-client/session'
import './styles/tokens.css'
import './styles/app.css'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App client={new HttpDroidScopeClient(readSessionToken(window.location, sessionStorage))} />
  </StrictMode>,
)
