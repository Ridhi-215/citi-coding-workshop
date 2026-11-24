import '@testing-library/jest-dom';

// jsdom has no matchMedia; react-responsive and MUI need it. Default to a desktop-width screen.
if (!window.matchMedia) {
  window.matchMedia = (query) => ({
    matches: /min-width/.test(query),
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  });
}

// jsdom lacks TextEncoder, which react-router uses.
import { TextDecoder, TextEncoder } from 'util';
Object.assign(globalThis, { TextEncoder, TextDecoder });
