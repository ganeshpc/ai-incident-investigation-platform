import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import App from './App.tsx'

// render draws the component into a fake page. jsdom is that fake page.
// screen reads the text the way a person would look for it.
describe('App', () => {
  it('identifies the synthetic test environment', () => {
    render(<App />)
    expect(screen.getByText('Synthetic test environment')).toBeTruthy()
    expect(
      screen.getByRole('heading', { name: 'AI incident investigation' }),
    ).toBeTruthy()
  })
})
