import { CssBaseline, ThemeProvider } from '@mui/material';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout';
import ProtectedRoute from './components/ProtectedRoute';
import RequirePermission from './components/RequirePermission';
import { AuthProvider } from './context/AuthContext';
import { NotifyProvider } from './context/NotifyContext';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import DashboardPage from './pages/DashboardPage';
import DeliverablesPage from './pages/DeliverablesPage';
import MemberPage from './pages/MemberPage';
import ProjectDetailPage from './pages/ProjectDetailPage';
import ProjectsPage from './pages/ProjectsPage';
import RegisterPage from './pages/RegisterPage';
import TeamPage from './pages/TeamPage';
import UsersPage from './pages/UsersPage';
import theme from './theme';

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <NotifyProvider>
        <AuthProvider>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/" element={<ProtectedRoute><AppLayout /></ProtectedRoute>}>
                <Route index element={<Navigate to="/dashboard" replace />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/projects" element={<ProjectsPage />} />
                <Route path="/projects/:id" element={<ProjectDetailPage />} />
                <Route path="/deliverables" element={<DeliverablesPage />} />
                <Route path="/team" element={<TeamPage />} />
                <Route path="/team/:id" element={<MemberPage />} />
                <Route path="/users" element={<RequirePermission permission="MANAGE_USERS"><UsersPage /></RequirePermission>} />
                <Route path="*" element={<NotFoundPage />} />
              </Route>
            </Routes>
          </BrowserRouter>
        </AuthProvider>
      </NotifyProvider>
    </ThemeProvider>
  );
}
