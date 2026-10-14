import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Card,
  CardContent,
  IconButton,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import EditOutlined from '@mui/icons-material/EditOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useChecklistTemplate, useSaveChecklistTemplate } from '../../api/closeout.js';
import { roleLabel } from '../../domain/procurement.js';
import { ItemDialog } from './ChecklistSection.jsx';

let key = 0;
const withKeys = (items) => items.map((i) => ({ ...i, key: `t-${++key}` }));

function Editor({ view }) {
  const save = useSaveChecklistTemplate();
  const [items, setItems] = useState(() => withKeys(view.items));
  const [editing, setEditing] = useState(null); // index, or -1 for a new item
  const [dirty, setDirty] = useState(false);
  const change = (next) => {
    setItems(next);
    setDirty(true);
  };
  const move = (i, d) => {
    const next = [...items];
    [next[i], next[i + d]] = [next[i + d], next[i]];
    change(next);
  };

  return (
    <>
      {view.usingDefaults && (
        <Alert severity="info" sx={{ mb: 2 }}>
          This is the built-in list. Saving makes it your organisation&apos;s own list.
        </Alert>
      )}
      {save.error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {save.error.message}
        </Alert>
      )}
      {save.isSuccess && !dirty && (
        <Alert severity="success" sx={{ mb: 2 }}>
          Saved. Projects that start closeout from now on get this list.
        </Alert>
      )}
      <Card>
        <CardContent>
          <TableContainer>
            <Table size="small" sx={{ minWidth: 700 }}>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 40 }}>#</TableCell>
                  <TableCell>Item</TableCell>
                  <TableCell>Responsible</TableCell>
                  <TableCell>Evidence</TableCell>
                  {view.canEdit && <TableCell sx={{ width: 170 }} />}
                </TableRow>
              </TableHead>
              <TableBody>
                {items.map((i, idx) => (
                  <TableRow key={i.key}>
                    <TableCell>{idx + 1}</TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>
                        {i.title}
                      </Typography>
                      {i.description && (
                        <Typography variant="caption" color="text.secondary">
                          {i.description}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{i.responsibleRole ? roleLabel(i.responsibleRole) : '—'}</TableCell>
                    <TableCell>{i.evidenceRequired ? 'Required' : '—'}</TableCell>
                    {view.canEdit && (
                      <TableCell align="right">
                        <IconButton size="small" disabled={idx === 0} onClick={() => move(idx, -1)} aria-label="Move up">
                          <ArrowUpward fontSize="small" />
                        </IconButton>
                        <IconButton size="small" disabled={idx === items.length - 1} onClick={() => move(idx, 1)} aria-label="Move down">
                          <ArrowDownward fontSize="small" />
                        </IconButton>
                        <Tooltip title="Edit">
                          <IconButton size="small" onClick={() => setEditing(idx)} aria-label={`Edit ${i.title}`}>
                            <EditOutlined fontSize="small" />
                          </IconButton>
                        </Tooltip>
                        <Tooltip title="Remove">
                          <IconButton size="small" onClick={() => change(items.filter((_x, j) => j !== idx))} aria-label={`Remove ${i.title}`}>
                            <DeleteOutline fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    )}
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {view.canEdit && (
            <Stack direction="row" spacing={1} sx={{ mt: 2, justifyContent: 'space-between' }}>
              <Button startIcon={<AddIcon />} onClick={() => setEditing(-1)}>
                Add item
              </Button>
              <Button
                variant="contained"
                disabled={!dirty || items.length === 0}
                loading={save.isPending}
                onClick={() =>
                  save.mutate(
                    items.map(({ title, description, responsibleRole, evidenceRequired }) => ({
                      title,
                      description,
                      responsibleRole,
                      evidenceRequired,
                    })),
                    { onSuccess: () => setDirty(false) },
                  )
                }
              >
                Save checklist
              </Button>
            </Stack>
          )}
        </CardContent>
      </Card>
      {editing !== null && (
        <ItemDialog
          item={editing >= 0 ? items[editing] : null}
          onClose={() => setEditing(null)}
          onSave={(body) => {
            change(editing >= 0 ? items.map((x, j) => (j === editing ? { ...x, ...body } : x)) : [...items, ...withKeys([body])]);
            setEditing(null);
          }}
        />
      )}
    </>
  );
}

Editor.propTypes = { view: PropTypes.object.isRequired };

export default function ChecklistTemplatePage() {
  const { data, isLoading, error } = useChecklistTemplate();
  return (
    <>
      <PageHeader
        title="Completion checklist"
        subtitle="The standard list each project starts closeout with. Projects can add or mark items not applicable."
      />
      <ErrorAlert error={error} title="Could not load the checklist" />
      {isLoading || !data ? (
        <Skeleton variant="rounded" height={300} />
      ) : (
        <>
          {!data.canEdit && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
              The PMO lead maintains this list.
            </Typography>
          )}
          <Editor view={data} />
        </>
      )}
    </>
  );
}
