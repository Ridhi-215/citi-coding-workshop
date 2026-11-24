import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/** Renders children only when the signed-in user has the permission; otherwise goes home. */
export default function RequirePermission({ permission, children }) {
  const { can } = useAuth();
  return can(permission) ? children : <Navigate to="/" replace />;
}
