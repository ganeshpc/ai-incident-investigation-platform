/**
 * The only screen in the Phase 0 UI.
 *
 * A function component is a function that returns the elements React should
 * draw. React is the library that updates the page. This screen has no
 * changing values. It exists so a person can see that the browser app is the
 * synthetic test environment and that investigation screens are not built yet.
 */
export default function App() {
  return (
    <main>
      <p className="banner">Synthetic test environment</p>
      <h1>AI incident investigation</h1>
      <p>
        This checkout is Phase 0. Incident submission, evidence, and reports
        arrive in later phases. Nothing on this page came from a production
        system.
      </p>
    </main>
  )
}
