// Jest runs the React components in jsdom. Babel is configured here only (Vite does not use it).
module.exports = {
  testEnvironment: 'jsdom',
  roots: ['<rootDir>/src'],
  setupFilesAfterEnv: ['<rootDir>/src/setupTests.js'],
  transform: {
    '^.+\\.[jt]sx?$': ['babel-jest', {
      presets: [['@babel/preset-env', { targets: { node: 'current' } }], ['@babel/preset-react', { runtime: 'automatic' }]],
      plugins: ['babel-plugin-transform-vite-meta-env'],
    }],
  },
  moduleNameMapper: { '\\.(css)$': '<rootDir>/src/test/styleStub.js' },
  collectCoverageFrom: ['src/**/*.{js,jsx}', '!src/main.jsx', '!src/test/**', '!src/setupTests.js'],
};
