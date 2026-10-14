import { useQueryClient } from '@tanstack/react-query';
import { Chip, MenuItem, TextField, Tooltip } from '@mui/material';
import { DEV_MODE, getDevUserId, setDevUserId } from '../api/client.js';
import { useDevUsers, useMe } from '../api/me.js';
import { roleLabel } from '../domain/procurement.js';
import UserMenu from './UserMenu.jsx';

/**
 * Shows who you are. In development it is a "Viewing as" picker, so one person can
 * play requester and each approver in turn; on servers, the signed-in person's menu.
 */
export default function UserSwitcher() {
  const qc = useQueryClient();
  const { data: me } = useMe();
  const { data: users } = useDevUsers();

  if (!DEV_MODE) {
    return <UserMenu />;
  }

  const change = (e) => {
    setDevUserId(e.target.value);
    qc.invalidateQueries(); // everything depends on who is looking
  };

  return (
    <Tooltip title="Development only: act as another sample user to try approval chains">
      <TextField
        select
        size="small"
        label="Viewing as"
        value={me?.id ?? getDevUserId() ?? ''}
        onChange={change}
        sx={{ minWidth: { xs: 160, sm: 260 } }}
      >
        {(users ?? []).map((u) => (
          <MenuItem key={u.id} value={u.id}>
            {u.displayName}
            {u.roles?.length > 0 && (
              <Chip size="small" label={roleLabel(u.roles[0])} sx={{ ml: 1 }} variant="outlined" />
            )}
          </MenuItem>
        ))}
        {!users?.length && me && <MenuItem value={me.id}>{me.displayName}</MenuItem>}
      </TextField>
    </Tooltip>
  );
}
