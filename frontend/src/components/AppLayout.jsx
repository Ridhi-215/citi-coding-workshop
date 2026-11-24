import { useState } from 'react';
import {
  AppBar, Avatar, Box, Chip, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon,
  ListItemText, Toolbar, Tooltip, Typography,
} from '@mui/material';
import MenuIcon from '@mui/icons-material/Menu';
import LogoutIcon from '@mui/icons-material/Logout';
import FolderIcon from '@mui/icons-material/FolderOutlined';
import { NavLink, Outlet } from 'react-router-dom';
import { useMediaQuery } from 'react-responsive';
import { useAuth } from '../context/AuthContext';
import { labelize } from '../utils/format';

const DRAWER_WIDTH = 240;

export const NAV_ITEMS = [
  { to: '/projects', label: 'Projects', icon: <FolderIcon /> },
];

export default function AppLayout() {
  const isMobile = useMediaQuery({ maxWidth: 899 });
  const [open, setOpen] = useState(false);
  const { user, logout } = useAuth();

  const drawer = (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
      <Toolbar>
        <Typography variant="h6" fontWeight={700} color="primary">ACME Tracker</Typography>
      </Toolbar>
      <Divider />
      <List component="nav" aria-label="Main navigation">
        {NAV_ITEMS.map((item) => (
          <ListItemButton
            key={item.to}
            component={NavLink}
            to={item.to}
            onClick={() => setOpen(false)}
            sx={{ '&.active': { bgcolor: 'action.selected', color: 'primary.main' } }}
          >
            <ListItemIcon sx={{ color: 'inherit' }}>{item.icon}</ListItemIcon>
            <ListItemText primary={item.label} />
          </ListItemButton>
        ))}
      </List>
    </Box>
  );

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', bgcolor: 'background.default' }}>
      <AppBar
        position="fixed"
        elevation={0}
        color="inherit"
        sx={{ borderBottom: 1, borderColor: 'divider', ml: { md: `${DRAWER_WIDTH}px` }, width: { md: `calc(100% - ${DRAWER_WIDTH}px)` } }}
      >
        <Toolbar sx={{ gap: 1 }}>
          {isMobile && (
            <IconButton edge="start" aria-label="Open navigation" onClick={() => setOpen(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <Box sx={{ flexGrow: 1 }} />
          <Chip size="small" label={labelize(user?.role)} color="primary" variant="outlined" />
          {!isMobile && <Typography variant="body2">{user?.full_name}</Typography>}
          <Avatar sx={{ width: 32, height: 32, fontSize: 14 }}>{user?.full_name?.[0]}</Avatar>
          <Tooltip title="Sign out">
            <IconButton aria-label="Sign out" onClick={logout}><LogoutIcon /></IconButton>
          </Tooltip>
        </Toolbar>
      </AppBar>
      <Drawer
        variant={isMobile ? 'temporary' : 'permanent'}
        open={isMobile ? open : true}
        onClose={() => setOpen(false)}
        ModalProps={{ keepMounted: true }}
        sx={{ width: DRAWER_WIDTH, flexShrink: 0, '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
      >
        {drawer}
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3 } }}>
        <Toolbar />
        <Outlet />
      </Box>
    </Box>
  );
}
