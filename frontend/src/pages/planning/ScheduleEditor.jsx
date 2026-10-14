import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  AppBar,
  Box,
  Button,
  Dialog,
  IconButton,
  MenuItem,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import CloseOutlined from '@mui/icons-material/CloseOutlined';
import FormatIndentIncrease from '@mui/icons-material/FormatIndentIncrease';
import FormatIndentDecrease from '@mui/icons-material/FormatIndentDecrease';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import AddOutlined from '@mui/icons-material/AddOutlined';
import { useSaveSchedule } from '../../api/planning.js';
import { useUsers } from '../../api/me.js';
import { formatPredecessors, parentIndexes, parsePredecessors } from '../../domain/planning.js';
import { todayInput } from '../../domain/contracts.js';

let counter = 0;
const newKey = () => `new-${(counter += 1)}`;
const blankRow = (level) => ({
  key: newKey(),
  level,
  name: '',
  days: '1',
  earliestStart: '',
  weight: '',
  ownerId: '',
  predKeys: [],
  predText: null,
});

/** Predecessors are kept as row keys, so moving rows never breaks them; shown as row numbers. */
function predDisplay(row, rowNumberOf) {
  if (row.predText !== null) return row.predText;
  return formatPredecessors(
    row.predKeys.filter((p) => rowNumberOf[p.key]).map((p) => ({ row: rowNumberOf[p.key], type: p.type, lagDays: p.lagDays })),
  );
}

function textToKeys(text, rows) {
  return parsePredecessors(text).map((p) => {
    if (p.row < 1 || p.row > rows.length) throw new Error(`There is no row ${p.row}`);
    return { key: rows[p.row - 1].key, type: p.type, lagDays: p.lagDays };
  });
}

function fromView(view) {
  return (view?.activities ?? []).map((a) => ({
    key: a.id,
    level: a.depth,
    name: a.name,
    days: a.summary ? '' : String(a.durationDays),
    earliestStart: a.earliestStart ?? '',
    weight: a.weight == null ? '' : String(Number(a.weight)),
    ownerId: a.owner?.id ?? '',
    predKeys: a.predecessors.map((p) => ({ key: p.id, type: p.type, lagDays: p.lagDays })),
    predText: null,
    hasProgress: Number(a.percent) > 0 && !a.summary,
  }));
}

/**
 * Full-screen editor for the whole schedule. Rows are indented to build the outline (a row
 * with rows indented under it is a summary). Predecessors use row numbers, as in MS Project:
 * "3" (finish-to-start), "3SS" (start-to-start), "3FS+2" (with a 2-day lag).
 */
export default function ScheduleEditor({ projectId, view, onClose }) {
  const { data: users } = useUsers();
  const save = useSaveSchedule(projectId);
  const [startDate, setStartDate] = useState(view?.startDate ?? todayInput());
  const [rows, setRows] = useState(() => {
    const r = fromView(view);
    return r.length ? r : [blankRow(0)];
  });
  const [localError, setLocalError] = useState(null);

  const isSummary = useMemo(() => rows.map((r, i) => i + 1 < rows.length && rows[i + 1].level > r.level), [rows]);
  const rowNumberOf = useMemo(() => Object.fromEntries(rows.map((r, i) => [r.key, i + 1])), [rows]);
  const commitPreds = (i) =>
    setRows((rs) => {
      const r = rs[i];
      if (r.predText === null) return rs;
      try {
        const keys = textToKeys(r.predText, rs);
        setLocalError(null);
        return rs.map((x, j) => (j === i ? { ...x, predKeys: keys, predText: null } : x));
      } catch (e) {
        setLocalError(`Row ${i + 1}: ${e.message}`);
        return rs;
      }
    });
  const update = (i, patch) => setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const insertAfter = (i) =>
    setRows((rs) => {
      const level = i >= 0 ? rs[i].level : 0;
      const next = [...rs];
      next.splice(i + 1, 0, blankRow(level));
      return next;
    });
  const move = (i, d) =>
    setRows((rs) => {
      const j = i + d;
      if (j < 0 || j >= rs.length) return rs;
      const next = [...rs];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });

  const submit = () => {
    setLocalError(null);
    try {
      const parents = parentIndexes(rows);
      const activities = rows.map((r, i) => {
        if (!r.name.trim()) throw new Error(`Row ${i + 1} needs a name`);
        const summary = isSummary[i];
        if (!summary && !/^\d+$/.test(String(r.days).trim())) throw new Error(`Row ${i + 1}: enter the duration in days`);
        let keys;
        try {
          keys = r.predText !== null ? textToKeys(r.predText, rows) : r.predKeys;
        } catch (e) {
          throw new Error(`Row ${i + 1}: ${e.message}`);
        }
        const preds = keys
          .filter((p) => rowNumberOf[p.key])
          .map((p) => {
            const n = rowNumberOf[p.key];
            if (n === i + 1) throw new Error(`Row ${i + 1} cannot depend on itself`);
            if (isSummary[n - 1] || summary) throw new Error(`Row ${i + 1}: link activities, not summary rows`);
            return p;
          });
        if (r.weight !== '' && !(Number(r.weight) >= 0)) throw new Error(`Row ${i + 1}: the weight is a number`);
        return {
          key: r.key,
          parentKey: parents[i] === null ? null : rows[parents[i]].key,
          name: r.name.trim(),
          durationDays: summary ? 0 : Number(r.days),
          earliestStart: r.earliestStart || null,
          weight: r.weight === '' ? null : Number(r.weight),
          ownerId: r.ownerId || null,
          predecessors: preds,
        };
      });
      save.mutate({ startDate, version: view?.version, activities }, { onSuccess: onClose });
    } catch (e) {
      setLocalError(e.message);
    }
  };

  const error = localError ?? save.error?.message;

  return (
    <Dialog fullScreen open onClose={onClose}>
      <AppBar position="sticky" color="default" elevation={1}>
        <Toolbar sx={{ gap: 2 }}>
          <IconButton edge="start" onClick={onClose} aria-label="Close">
            <CloseOutlined />
          </IconButton>
          <Typography variant="h6" sx={{ flex: 1 }}>
            Edit schedule
          </Typography>
          <TextField
            size="small"
            type="date"
            label="Schedule start"
            value={startDate}
            onChange={(e) => setStartDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
          <Button variant="contained" onClick={submit} loading={save.isPending}>
            Save schedule
          </Button>
        </Toolbar>
      </AppBar>
      <Box sx={{ p: 2 }}>
        <Alert severity="info" sx={{ mb: 2 }}>
          Durations are in calendar days (0 = milestone). Indent rows to group them under a summary. Predecessors use
          row numbers: <strong>3</strong> finish-to-start, <strong>3SS</strong> start-to-start, <strong>3FS+2</strong>{' '}
          with a 2-day lag. Dates are worked out from the links; &quot;not before&quot; holds an activity back.
        </Alert>
        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}
        <TableContainer>
          <Table size="small" sx={{ minWidth: 1100 }}>
            <TableHead>
              <TableRow>
                <TableCell sx={{ width: 40 }}>#</TableCell>
                <TableCell sx={{ width: 150 }} />
                <TableCell>Activity</TableCell>
                <TableCell sx={{ width: 90 }}>Days</TableCell>
                <TableCell sx={{ width: 160 }}>Not before</TableCell>
                <TableCell sx={{ width: 150 }}>Predecessors</TableCell>
                <TableCell sx={{ width: 200 }}>Owner</TableCell>
                <TableCell sx={{ width: 90 }}>Weight</TableCell>
                <TableCell sx={{ width: 90 }} />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((r, i) => (
                <TableRow key={r.key}>
                  <TableCell>{i + 1}</TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Outdent">
                      <span>
                        <IconButton size="small" disabled={r.level === 0} onClick={() => update(i, { level: r.level - 1 })}>
                          <FormatIndentDecrease fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <Tooltip title="Indent under the row above">
                      <span>
                        <IconButton
                          size="small"
                          disabled={i === 0 || r.level > rows[i - 1].level}
                          onClick={() => update(i, { level: r.level + 1 })}
                        >
                          <FormatIndentIncrease fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                    <IconButton size="small" aria-label="Move up" onClick={() => move(i, -1)}>
                      <ArrowUpward fontSize="small" />
                    </IconButton>
                    <IconButton size="small" aria-label="Move down" onClick={() => move(i, 1)}>
                      <ArrowDownward fontSize="small" />
                    </IconButton>
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      fullWidth
                      value={r.name}
                      onChange={(e) => update(i, { name: e.target.value })}
                      placeholder="Activity name"
                      sx={{ pl: r.level * 3 }}
                      slotProps={{ htmlInput: { style: { fontWeight: isSummary[i] ? 600 : 400 }, maxLength: 200 } }}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      value={isSummary[i] ? '' : r.days}
                      disabled={isSummary[i]}
                      placeholder={isSummary[i] ? 'summary' : ''}
                      onChange={(e) => update(i, { days: e.target.value.replace(/\D/g, '') })}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      type="date"
                      value={r.earliestStart}
                      disabled={isSummary[i]}
                      onChange={(e) => update(i, { earliestStart: e.target.value })}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      value={predDisplay(r, rowNumberOf)}
                      disabled={isSummary[i]}
                      placeholder="e.g. 2, 3SS+1"
                      onChange={(e) => update(i, { predText: e.target.value })}
                      onBlur={() => commitPreds(i)}
                    />
                  </TableCell>
                  <TableCell>
                    <TextField select size="small" fullWidth value={r.ownerId} onChange={(e) => update(i, { ownerId: e.target.value })}>
                      <MenuItem value="">No owner</MenuItem>
                      {(users ?? []).map((u) => (
                        <MenuItem key={u.id} value={u.id}>
                          {u.displayName}
                        </MenuItem>
                      ))}
                    </TextField>
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      value={r.weight}
                      disabled={isSummary[i]}
                      placeholder="days"
                      onChange={(e) => update(i, { weight: e.target.value })}
                    />
                  </TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>
                    <Tooltip title="Add a row below">
                      <IconButton size="small" onClick={() => insertAfter(i)}>
                        <AddOutlined fontSize="small" />
                      </IconButton>
                    </Tooltip>
                    <Tooltip title={r.hasProgress ? 'Has progress recorded' : 'Remove'}>
                      <span>
                        <IconButton
                          size="small"
                          disabled={r.hasProgress || rows.length === 1}
                          onClick={() =>
                            setRows((rs) =>
                              rs
                                .filter((_x, j) => j !== i)
                                .map((x) => ({ ...x, predKeys: x.predKeys.filter((p) => p.key !== r.key) })),
                            )
                          }
                        >
                          <DeleteOutline fontSize="small" />
                        </IconButton>
                      </span>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <Stack direction="row" sx={{ mt: 2 }}>
          <Button startIcon={<AddOutlined />} onClick={() => insertAfter(rows.length - 1)}>
            Add row
          </Button>
        </Stack>
      </Box>
    </Dialog>
  );
}

ScheduleEditor.propTypes = {
  projectId: PropTypes.string.isRequired,
  view: PropTypes.object,
  onClose: PropTypes.func.isRequired,
};
