import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { Alert, Snackbar } from '@mui/material';

const NotifyContext = createContext(() => {});

export function NotifyProvider({ children }) {
  const [message, setMessage] = useState(null);

  const notify = useCallback((text, severity = 'success') => {
    setMessage({ text, severity, key: Date.now() });
  }, []);

  const value = useMemo(() => notify, [notify]);
  return (
    <NotifyContext.Provider value={value}>
      {children}
      <Snackbar
        key={message?.key}
        open={Boolean(message)}
        autoHideDuration={4000}
        onClose={(_, reason) => reason !== 'clickaway' && setMessage(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      >
        {message ? (
          <Alert severity={message.severity} variant="filled" onClose={() => setMessage(null)} sx={{ width: '100%' }}>
            {message.text}
          </Alert>
        ) : undefined}
      </Snackbar>
    </NotifyContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export const useNotify = () => useContext(NotifyContext);
