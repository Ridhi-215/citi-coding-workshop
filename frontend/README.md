# ACME Project Tracker - Frontend

React 19 + Material UI single-page app. See the [main README](../README.md) for the
architecture, setup and demo accounts.

## Commands

```sh
npm install
npm run dev            # http://localhost:3000 (normally started by ../bin/start-dev.sh)
npm run lint
npm test               # Jest + Testing Library
npm run test:coverage  # coverage report in coverage/
npm run build          # production build in dist/ (used by ../bin/deploy-frontend.sh)
```

The API base URL comes from `VITE_API_URL` (`.env.local`, written by `../bin/generate-env.sh`
locally; set by `../bin/deploy-frontend.sh` for AWS). Requests go to
`${VITE_API_URL}/api/<service>/<path>`.

## Structure

```text
src/pages/       one component per route (login, register, dashboard, projects, ...)
src/components/  layout, dialogs, tables and small display components
src/context/     AuthContext (session, permissions) and NotifyContext (snackbar messages)
src/services/    API client (tokens, refresh, errors) and one module per backend service
src/utils/       formatting, validation rules and chart colors
src/hooks.js     useQuery (data loading), useDebounced, useOptions
```
