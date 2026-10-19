import { useState } from 'react';
import PropTypes from 'prop-types';
import { NavLink, useLocation } from 'react-router';
import {
  AppBar,
  Box,
  Chip,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  ListSubheader,
  Stack,
  Toolbar,
  Typography,
  useMediaQuery,
} from '@mui/material';
import { useTheme } from '@mui/material/styles';
import MenuIcon from '@mui/icons-material/Menu';
import DashboardOutlined from '@mui/icons-material/DashboardOutlined';
import FolderOutlined from '@mui/icons-material/FolderOutlined';
import GavelOutlined from '@mui/icons-material/GavelOutlined';
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined';
import AssessmentOutlined from '@mui/icons-material/AssessmentOutlined';
import ReceiptLongOutlined from '@mui/icons-material/ReceiptLongOutlined';
import NotificationsMenu from './NotificationsMenu.jsx';
import UserSwitcher from './UserSwitcher.jsx';
import { DEV_MODE } from '../api/client.js';
import TuneOutlined from '@mui/icons-material/TuneOutlined';
import StorefrontOutlined from '@mui/icons-material/StorefrontOutlined';
import EventNoteOutlined from '@mui/icons-material/EventNoteOutlined';
import LightbulbOutlined from '@mui/icons-material/LightbulbOutlined';
import TrendingUpOutlined from '@mui/icons-material/TrendingUpOutlined';
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined';
import ManageAccountsOutlined from '@mui/icons-material/ManageAccountsOutlined';
import { hasRole, useMe } from '../api/me.js';

const DRAWER_WIDTH = 248;

const NAV = [
  {
    section: 'Portfolio',
    items: [
      { to: '/dashboard', label: 'Dashboard', icon: <DashboardOutlined /> },
      { to: '/projects', label: 'Projects', icon: <FolderOutlined /> },
      { to: '/benefits', label: 'Benefits', icon: <TrendingUpOutlined /> },
      { to: '/lessons', label: 'Lessons learned', icon: <LightbulbOutlined /> },
    ],
  },
  {
    section: 'Lifecycle',
    items: [
      { to: '/procurement-plans', label: 'Procurement plan', icon: <EventNoteOutlined /> },
      { to: '/requisitions', label: 'Requisitions', icon: <ReceiptLongOutlined /> },
      { to: '/tenders', label: 'Tenders', icon: <GavelOutlined /> },
      { to: '/suppliers', label: 'Suppliers', icon: <StorefrontOutlined /> },
      { to: '/contracts', label: 'Contracts', icon: <DescriptionOutlined /> },
      { to: '/reports', label: 'Reports', icon: <AssessmentOutlined /> },
    ],
  },
  {
    section: 'Administration',
    items: [
      { to: '/settings/procurement', label: 'Procurement settings', icon: <TuneOutlined /> },
      { to: '/settings/closeout-checklist', label: 'Completion checklist', icon: <FactCheckOutlined /> },
      { to: '/settings/users', label: 'Users and roles', icon: <ManageAccountsOutlined />, role: 'SYSTEM_ADMIN' },
    ],
  },
];

function NavContent({ onNavigate }) {
  const { pathname } = useLocation();
  const { data: me } = useMe();
  return (
    <Box sx={{ overflowY: 'auto' }}>
      <Toolbar sx={{ px: 2.5 }}>
        <Typography variant="h6" component="div" sx={{ fontWeight: 700 }}>
          PM Platform
        </Typography>
      </Toolbar>
      <Divider />
      {NAV.map((group) => (
        <List
          key={group.section}
          dense
          subheader={<ListSubheader sx={{ bgcolor: 'transparent' }}>{group.section}</ListSubheader>}
        >
          {group.items.filter((item) => !item.role || hasRole(me, item.role)).map((item) => (
            <ListItemButton
              key={item.to}
              component={NavLink}
              to={item.to}
              selected={pathname.startsWith(item.to)}
              onClick={onNavigate}
              sx={{ mx: 1, borderRadius: 1 }}
            >
              <ListItemIcon sx={{ minWidth: 36 }}>{item.icon}</ListItemIcon>
              <ListItemText primary={item.label} />
              {item.soon && <Chip label="Soon" size="small" variant="outlined" />}
            </ListItemButton>
          ))}
        </List>
      ))}
    </Box>
  );
}

NavContent.propTypes = {
  onNavigate: PropTypes.func,
};

export default function AppShell({ children }) {
  const theme = useTheme();
  const isDesktop = useMediaQuery(theme.breakpoints.up('md'));
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar
        position="fixed"
        color="inherit"
        elevation={0}
        sx={{
          borderBottom: 1,
          borderColor: 'divider',
          width: { md: `calc(100% - ${DRAWER_WIDTH}px)` },
          ml: { md: `${DRAWER_WIDTH}px` },
        }}
      >
        <Toolbar>
          {!isDesktop && (
            <IconButton edge="start" aria-label="Open navigation" onClick={() => setMobileOpen(true)}>
              <MenuIcon />
            </IconButton>
          )}
          <Typography variant="subtitle1" sx={{ flexGrow: 1, ml: isDesktop ? 0 : 1 }}>
            Kenya Power
          </Typography>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            {DEV_MODE && (
              <Chip label="Development" color="warning" size="small" sx={{ display: { xs: 'none', sm: 'flex' } }} />
            )}
            <UserSwitcher />
            <NotificationsMenu />
          </Stack>
        </Toolbar>
      </AppBar>

      <Box component="nav" sx={{ width: { md: DRAWER_WIDTH }, flexShrink: { md: 0 } }}>
        {isDesktop ? (
          <Drawer
            variant="permanent"
            open
            sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}
          >
            <NavContent />
          </Drawer>
        ) : (
          <Drawer
            variant="temporary"
            open={mobileOpen}
            onClose={() => setMobileOpen(false)}
            ModalProps={{ keepMounted: true }}
            sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH } }}
          >
            <NavContent onNavigate={() => setMobileOpen(false)} />
          </Drawer>
        )}
      </Box>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 4 } }}>
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}

AppShell.propTypes = {
  children: PropTypes.node.isRequired,
};
