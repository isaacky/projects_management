import { useState } from 'react';
import { useNavigate } from 'react-router';
import {
  Badge,
  Box,
  Button,
  Divider,
  IconButton,
  ListItemText,
  Menu,
  MenuItem,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import NotificationsOutlined from '@mui/icons-material/NotificationsOutlined';
import { useInbox, useMarkAllRead, useMarkRead } from '../api/notifications.js';

function timeAgo(iso) {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60_000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  return new Date(iso).toLocaleDateString('en-KE');
}

/** Bell icon with the in-app inbox. */
export default function NotificationsMenu() {
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState(null);
  const { data } = useInbox();
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const unread = data?.unreadCount ?? 0;

  const open = (n) => {
    setAnchor(null);
    if (!n.readAt) markRead.mutate(n.id);
    if (n.link) navigate(n.link);
  };

  return (
    <>
      <Tooltip title="Notifications">
        <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label={`Notifications, ${unread} unread`}>
          <Badge badgeContent={unread} color="error" max={99}>
            <NotificationsOutlined />
          </Badge>
        </IconButton>
      </Tooltip>
      <Menu
        anchorEl={anchor}
        open={Boolean(anchor)}
        onClose={() => setAnchor(null)}
        slotProps={{ paper: { sx: { width: 380, maxWidth: 'calc(100vw - 32px)' } } }}
      >
        <Stack direction="row" sx={{ px: 2, py: 1, alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>Notifications</Typography>
          {unread > 0 && (
            <Button size="small" onClick={() => markAll.mutate()}>Mark all read</Button>
          )}
        </Stack>
        <Divider />
        {!data?.items?.length && (
          <Box sx={{ px: 2, py: 3 }}>
            <Typography variant="body2" color="text.secondary">Nothing here yet.</Typography>
          </Box>
        )}
        {data?.items?.map((n) => (
          <MenuItem
            key={n.id}
            onClick={() => open(n)}
            sx={{ whiteSpace: 'normal', alignItems: 'flex-start', bgcolor: n.readAt ? 'transparent' : 'action.hover' }}
          >
            <ListItemText
              primary={n.title}
              secondary={
                <>
                  {n.body && <Box component="span" sx={{ display: 'block' }}>{n.body}</Box>}
                  <Box component="span" sx={{ display: 'block', mt: 0.5 }}>{timeAgo(n.createdAt)}</Box>
                </>
              }
              slotProps={{ primary: { sx: { fontWeight: n.readAt ? 400 : 600 } } }}
            />
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
