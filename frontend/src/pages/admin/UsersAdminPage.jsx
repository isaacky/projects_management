import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  IconButton,
  InputAdornment,
  Skeleton,
  Stack,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import EditOutlined from '@mui/icons-material/EditOutlined';
import SearchIcon from '@mui/icons-material/Search';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useAdminRoles, useAdminUsers, useSaveUser, useUnlinkUser } from '../../api/admin.js';
import { hasRole, useMe } from '../../api/me.js';
import { roleLabel, toRoleCode } from '../../domain/procurement.js';
import { matchesUser, roleOptions, userForm, userRequest, validateUser } from '../../domain/users.js';
import { formatDateTime } from '../../domain/tenders.js';

function UserDialog({ user, roles, isSelf, onClose }) {
  const save = useSaveUser();
  const unlink = useUnlinkUser();
  const [form, setForm] = useState(() => userForm(user));
  const [touched, setTouched] = useState(false);
  const errors = validateUser(form);
  const set = (patch) => setForm((f) => ({ ...f, ...patch }));
  const describe = useMemo(() => Object.fromEntries((roles ?? []).map((r) => [r.code, r.description])), [roles]);

  const submit = () => {
    setTouched(true);
    if (Object.keys(errors).length) return;
    save.mutate({ id: user?.id, body: userRequest(form) }, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>{user ? `Edit ${user.displayName}` : 'Add a person'}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {(save.error || unlink.error) && <Alert severity="error">{(save.error || unlink.error).message}</Alert>}
          <TextField
            label="Email"
            value={form.email}
            onChange={(e) => set({ email: e.target.value })}
            error={touched && Boolean(errors.email)}
            helperText={
              (touched && errors.email) || 'The address they sign in with. Their first sign-in links to this record.'
            }
            required
            autoFocus={!user}
            fullWidth
          />
          <TextField
            label="Name"
            value={form.displayName}
            onChange={(e) => set({ displayName: e.target.value })}
            error={touched && Boolean(errors.displayName)}
            helperText={touched && errors.displayName}
            required
            fullWidth
          />
          <Autocomplete
            multiple
            freeSolo
            options={roleOptions(roles)}
            value={form.roles}
            onChange={(_e, value) => set({ roles: [...new Set(value.map(toRoleCode).filter(Boolean))] })}
            getOptionLabel={(code) => roleLabel(code)}
            renderOption={(props, code) => {
              const { key, ...rest } = props;
              return (
                <li key={key} {...rest}>
                  <Box>
                    <Typography variant="body2">{roleLabel(code)}</Typography>
                    {describe[code] && (
                      <Typography variant="caption" color="text.secondary">
                        {describe[code]}
                      </Typography>
                    )}
                  </Box>
                </li>
              );
            }}
            renderValue={(value, getItemProps) =>
              value.map((code, index) => {
                const { key, ...rest } = getItemProps({ index });
                return <Chip key={key} size="small" label={roleLabel(code)} {...rest} />;
              })
            }
            renderInput={(params) => (
              <TextField
                {...params}
                label="Roles"
                error={touched && Boolean(errors.roles)}
                helperText={
                  (touched && errors.roles) ||
                  'Pick from the list, or type another role code named in the approval thresholds.'
                }
              />
            )}
          />
          {user && (
            <FormControlLabel
              control={
                <Switch checked={form.active} onChange={(e) => set({ active: e.target.checked })} disabled={isSelf} />
              }
              label={form.active ? 'Active' : 'Inactive (cannot use the platform; history is kept)'}
            />
          )}
          {isSelf && (
            <Typography variant="caption" color="text.secondary">
              This is you: you can remove your own roles (except administrator), but another administrator has
              to give you new ones, deactivate you or remove your administrator role.
            </Typography>
          )}
          {user?.linked && (
            <Alert
              severity="info"
              action={
                <Button color="inherit" size="small" onClick={() => unlink.mutate(user.id, { onSuccess: onClose })}>
                  Unlink
                </Button>
              }
            >
              Linked to a sign-in account. Unlink only if their account was recreated in the identity provider;
              their next sign-in with this email links again.
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>
          {user ? 'Save' : 'Add'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

UserDialog.propTypes = {
  user: PropTypes.object,
  roles: PropTypes.array,
  isSelf: PropTypes.bool,
  onClose: PropTypes.func.isRequired,
};

export default function UsersAdminPage() {
  const { data: me } = useMe();
  const users = useAdminUsers();
  const roles = useAdminRoles();
  const [query, setQuery] = useState('');
  const [editing, setEditing] = useState(null); // a user, 'new', or null
  const [showInactive, setShowInactive] = useState(false);

  if (me && !hasRole(me, 'SYSTEM_ADMIN')) {
    return (
      <>
        <PageHeader title="Users and roles" />
        <Alert severity="info">Only system administrators can manage users and roles.</Alert>
      </>
    );
  }

  const list = (users.data ?? []).filter((u) => (showInactive || u.active) && matchesUser(u, query));

  return (
    <>
      <PageHeader
        title="Users and roles"
        subtitle="Who can use the platform and what they may approve. Sign-in itself is managed in Keycloak."
        actions={
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>
            Add person
          </Button>
        }
      />
      <Alert severity="info" sx={{ mb: 2 }}>
        Create the person&apos;s sign-in account in Keycloak (later, Active Directory) with a verified email, and
        add them here with the same email. Their first sign-in links the two.
      </Alert>
      {users.error && <ErrorAlert error={users.error} title="Could not load users" />}
      <Card>
        <CardContent>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ mb: 2, alignItems: { sm: 'center' } }}>
            <TextField
              size="small"
              placeholder="Search name, email or role"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              slotProps={{
                input: {
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon fontSize="small" />
                    </InputAdornment>
                  ),
                },
              }}
              sx={{ minWidth: { sm: 320 } }}
            />
            <FormControlLabel
              control={<Switch checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)} />}
              label="Show inactive"
            />
          </Stack>
          {users.isLoading ? (
            <Skeleton variant="rounded" height={240} />
          ) : (
            <TableContainer>
              <Table size="small" sx={{ minWidth: 720 }}>
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Email</TableCell>
                    <TableCell>Roles</TableCell>
                    <TableCell>Sign-in</TableCell>
                    <TableCell>Added</TableCell>
                    <TableCell sx={{ width: 56 }} />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {list.map((u) => (
                    <TableRow key={u.id} sx={{ opacity: u.active ? 1 : 0.6 }}>
                      <TableCell>
                        {u.displayName}
                        {!u.active && <Chip size="small" label="Inactive" sx={{ ml: 1 }} />}
                      </TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell>
                        <Stack direction="row" sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                          {u.roles.length === 0 && (
                            <Typography variant="body2" color="text.secondary">
                              —
                            </Typography>
                          )}
                          {u.roles.map((r) => (
                            <Chip key={r} size="small" variant="outlined" label={roleLabel(r)} />
                          ))}
                        </Stack>
                      </TableCell>
                      <TableCell>
                        {u.linked ? (
                          <Chip size="small" color="success" variant="outlined" label="Linked" />
                        ) : (
                          <Typography variant="body2" color="text.secondary">
                            Not signed in yet
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>{formatDateTime(u.createdAt)}</TableCell>
                      <TableCell align="right">
                        <Tooltip title="Edit">
                          <IconButton size="small" aria-label={`Edit ${u.displayName}`} onClick={() => setEditing(u)}>
                            <EditOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                  {list.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={6}>
                        <Typography variant="body2" color="text.secondary">
                          Nobody matches.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CardContent>
      </Card>

      <Card sx={{ mt: 3 }}>
        <CardContent>
          <Typography variant="h6" component="h2" gutterBottom>
            Roles
          </Typography>
          {roles.error && <ErrorAlert error={roles.error} title="Could not load roles" />}
          <Table size="small">
            <TableBody>
              {(roles.data ?? []).map((r) => (
                <TableRow key={r.code}>
                  <TableCell sx={{ whiteSpace: 'nowrap', fontWeight: 500 }}>{roleLabel(r.code)}</TableCell>
                  <TableCell>
                    {r.description || (
                      <Typography variant="body2" color="text.secondary">
                        Named in the approval thresholds.
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right" sx={{ whiteSpace: 'nowrap' }}>
                    {r.holders} {r.holders === 1 ? 'person' : 'people'}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </CardContent>
      </Card>

      {editing && (
        <UserDialog
          user={editing === 'new' ? null : editing}
          roles={roles.data}
          isSelf={editing !== 'new' && editing.id === me?.id}
          onClose={() => setEditing(null)}
        />
      )}
    </>
  );
}
