import { CssBaseline, ThemeProvider, createTheme } from '@mui/material';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import RequirePermission from './components/RequirePermission';
import { AuthProvider } from './context/AuthContext';
import { NotifyProvider } from './context/NotifyContext';
import LoginPage from './pages/LoginPage';
import ProjectsPage from './pages/ProjectsPage';
import UsersPage from './pages/UsersPage';

const theme = createTheme({
  palette: { primary: { main: '#2f5bd3' }, background: { default: '#f5f7fb' } },
  shape: { borderRadius: 10 },
  typography: { fontFamily: 'Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif' },
  components: { MuiButton: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } } },
});

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <NotifyProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/users" element={<RequirePermission permission="MANAGE_USERS"><UsersPage /></RequirePermission>} />
                <Route path="*" element={<Navigate to="/projects" replace />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </NotifyProvider>
    </ThemeProvider>
  );
}
