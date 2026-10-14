import { useState } from 'react';
import { Button, Divider, ListItemText, Menu, MenuItem, Typography } from '@mui/material';
import AccountCircleOutlined from '@mui/icons-material/AccountCircleOutlined';
import { useMe } from '../api/me.js';
import { signOut } from '../auth/session.js';
import { roleLabel } from '../domain/procurement.js';

/** The signed-in person (servers): name, email and roles, and Sign out. */
export default function UserMenu() {
  const { data: me } = useMe();
  const [anchor, setAnchor] = useState(null);
  if (!me) return null;
  return (
    <>
      <Button
        color="inherit"
        startIcon={<AccountCircleOutlined />}
        onClick={(e) => setAnchor(e.currentTarget)}
        aria-haspopup="menu"
        sx={{ textTransform: 'none', maxWidth: { xs: 160, sm: 280 } }}
      >
        <Typography variant="body2" noWrap>
          {me.displayName}
        </Typography>
      </Button>
      <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
        <MenuItem disabled sx={{ opacity: '1 !important' }}>
          <ListItemText
            primary={me.email}
            secondary={(me.roles ?? []).map(roleLabel).join(', ') || 'No roles yet'}
            slotProps={{ secondary: { sx: { whiteSpace: 'normal', maxWidth: 280 } } }}
          />
        </MenuItem>
        <Divider />
        <MenuItem onClick={signOut}>Sign out</MenuItem>
      </Menu>
    </>
  );
}
