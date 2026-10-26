import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// createRoot attaches React to the empty div in index.html.
// StrictMode renders the tree twice in development so unsafe side effects show up.
const rootElement = document.getElementById('root')
if (rootElement === null) {
  throw new Error('index.html is missing the root element')
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
