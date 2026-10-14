import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  IconButton,
  InputAdornment,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  Switch,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from '@mui/material';
import AddOutlined from '@mui/icons-material/AddOutlined';
import DeleteOutline from '@mui/icons-material/DeleteOutline';
import ArrowUpward from '@mui/icons-material/ArrowUpward';
import ArrowDownward from '@mui/icons-material/ArrowDownward';
import {
  useChangeCommittee,
  useDecide,
  useDeclare,
  useRejectBid,
  useSaveCommittee,
  useSaveCriteria,
  useSaveMarks,
  useSavePrice,
  useSign,
  useSubmitReport,
} from '../../../api/evaluation.js';
import { useUsers } from '../../../api/me.js';
import {
  COMMITTEE_ROLE,
  ROLE_LABEL,
  committeeIssues,
  criteriaOf,
  marksByCriterion,
  technicalTotal,
  validScore,
} from '../../../domain/evaluation.js';
import { inputToMinor, minorToInput } from '../../../utils/amountField.js';
import { formatMoney } from '../../../utils/money.js';

function ErrorBox({ error }) {
  return error ? (
    <Alert severity="error" sx={{ mb: 2 }}>
      {error.message}
    </Alert>
  ) : null;
}

ErrorBox.propTypes = { error: PropTypes.instanceOf(Error) };

const evalShape = PropTypes.object.isRequired;

// ------------------------------------------------------------------- criteria editor (also used in settings)
let rowKey = 0;
export const criterionRow = (c) => ({
  key: `r${(rowKey += 1)}`,
  stage: c.stage,
  name: c.name ?? '',
  description: c.description ?? '',
  maxScore: c.maxScore == null || c.maxScore === '' ? '' : String(Number(c.maxScore)),
});

export const criteriaRowsInvalid = (rows) =>
  !rows.some((r) => r.stage === 'PRELIMINARY') ||
  rows.some((r) => !r.name.trim() || (r.stage === 'TECHNICAL' && !(Number(r.maxScore) > 0)));

export const criteriaRowsToBody = (rows) =>
  rows.map((r) => ({
    stage: r.stage,
    name: r.name.trim(),
    description: r.description.trim() || null,
    maxScore: r.stage === 'TECHNICAL' ? Number(r.maxScore) : null,
  }));

/** Preliminary and technical criteria, editable in place. */
export function CriteriaEditor({ rows, setRows }) {
  const total = technicalTotal(rows.map((r) => ({ ...r, maxScore: Number(r.maxScore) || 0 })));
  const update = (key, patch) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...patch } : r)));
  const move = (key, delta) =>
    setRows((rs) => {
      const i = rs.findIndex((r) => r.key === key);
      let j = i + delta;
      while (j >= 0 && j < rs.length && rs[j].stage !== rs[i].stage) j += delta;
      if (j < 0 || j >= rs.length) return rs;
      const next = [...rs];
      [next[i], next[j]] = [next[j], next[i]];
      return next;
    });

  const section = (stage, title, help) => (
    <Box sx={{ mb: 3 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
        <Box>
          <Typography variant="subtitle1">{title}</Typography>
          <Typography variant="caption" color="text.secondary">
            {help}
          </Typography>
        </Box>
        <Button
          size="small"
          startIcon={<AddOutlined />}
          onClick={() => setRows((rs) => [...rs, criterionRow({ stage })])}
        >
          Add
        </Button>
      </Stack>
      <Stack spacing={1.5}>
        {rows
          .filter((r) => r.stage === stage)
          .map((r) => (
            <Stack key={r.key} direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ alignItems: 'flex-start' }}>
              <TextField
                size="small"
                label="Criterion"
                value={r.name}
                onChange={(e) => update(r.key, { name: e.target.value })}
                required
                sx={{ flex: 2 }}
              />
              <TextField
                size="small"
                label="Guidance (optional)"
                value={r.description}
                onChange={(e) => update(r.key, { description: e.target.value })}
                sx={{ flex: 2 }}
              />
              {stage === 'TECHNICAL' && (
                <TextField
                  size="small"
                  label="Max score"
                  value={r.maxScore}
                  onChange={(e) => update(r.key, { maxScore: e.target.value })}
                  required
                  sx={{ width: 110 }}
                  slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                />
              )}
              <Stack direction="row">
                <IconButton size="small" aria-label="Move up" onClick={() => move(r.key, -1)}>
                  <ArrowUpward fontSize="small" />
                </IconButton>
                <IconButton size="small" aria-label="Move down" onClick={() => move(r.key, 1)}>
                  <ArrowDownward fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  aria-label="Remove"
                  onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}
                >
                  <DeleteOutline fontSize="small" />
                </IconButton>
              </Stack>
            </Stack>
          ))}
      </Stack>
    </Box>
  );

  return (
    <>
      {section('PRELIMINARY', 'Preliminary (mandatory, pass/fail)', 'A bid that fails any of these is out.')}
      {section('TECHNICAL', 'Technical (scored)', `Total available: ${total}`)}
    </>
  );
}

CriteriaEditor.propTypes = { rows: PropTypes.array.isRequired, setRows: PropTypes.func.isRequired };

// ------------------------------------------------------------------- criteria (set-up)
export function CriteriaDialog({ tenderId, evaluation, onClose }) {
  const [rows, setRows] = useState(() =>
    [...criteriaOf(evaluation.criteria, 'PRELIMINARY'), ...criteriaOf(evaluation.criteria, 'TECHNICAL')].map(criterionRow),
  );
  const [passMark, setPassMark] = useState(evaluation.passMarkPct ?? '');
  const save = useSaveCriteria(tenderId);
  const hasTechnical = rows.some((r) => r.stage === 'TECHNICAL');
  const invalid = criteriaRowsInvalid(rows) || (hasTechnical && passMark === '');

  const submit = (e) => {
    e.preventDefault();
    save.mutate(
      { passMarkPct: passMark === '' ? null : Number(passMark), criteria: criteriaRowsToBody(rows) },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>Evaluation criteria</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          These must match the criteria published in the tender document. They are fixed once the committee is
          appointed.
        </DialogContentText>
        <ErrorBox error={save.error} />
        <CriteriaEditor rows={rows} setRows={setRows} />
        <TextField
          label="Technical pass mark"
          value={passMark}
          onChange={(e) => setPassMark(e.target.value.replace(/\D/g, '').slice(0, 3))}
          required={hasTechnical}
          sx={{ width: 220 }}
          slotProps={{ input: { endAdornment: <InputAdornment position="end">%</InputAdornment> } }}
          helperText="Percentage of the total technical score"
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={invalid} loading={save.isPending}>
          Save criteria
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CriteriaDialog.propTypes = { tenderId: PropTypes.string.isRequired, evaluation: evalShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- committee (set-up)
export function CommitteeDialog({ tenderId, evaluation, onClose }) {
  const { data: users } = useUsers();
  const [members, setMembers] = useState(evaluation.members.map((m) => ({ userId: m.userId, role: m.role })));
  const save = useSaveCommittee(tenderId);
  const byId = useMemo(() => Object.fromEntries((users ?? []).map((u) => [u.id, u])), [users]);
  const chosen = new Set(members.map((m) => m.userId));
  const issues = committeeIssues(members, evaluation.minMembers, evaluation.maxMembers);

  const submit = (e) => {
    e.preventDefault();
    save.mutate(members, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Proposed evaluation committee</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {evaluation.minMembers === evaluation.maxMembers
            ? `${evaluation.minMembers} voting members`
            : `${evaluation.minMembers} to ${evaluation.maxMembers} voting members`}{' '}
          including the chair, plus an optional secretary who does not vote. Quorum {evaluation.quorum}. The
          accounting officer appoints the committee.
        </DialogContentText>
        <ErrorBox error={save.error} />
        <Stack spacing={1.5}>
          {members.map((m, i) => (
            <Stack key={m.userId} direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography sx={{ flex: 1 }}>{byId[m.userId]?.displayName ?? '…'}</Typography>
              <TextField
                select
                size="small"
                value={m.role}
                onChange={(e) =>
                  setMembers((ms) => ms.map((x, j) => (j === i ? { ...x, role: e.target.value } : x)))
                }
                sx={{ width: 220 }}
              >
                {Object.entries(COMMITTEE_ROLE).map(([code, label]) => (
                  <MenuItem key={code} value={code}>
                    {label}
                  </MenuItem>
                ))}
              </TextField>
              <IconButton
                size="small"
                aria-label="Remove"
                onClick={() => setMembers((ms) => ms.filter((_x, j) => j !== i))}
              >
                <DeleteOutline fontSize="small" />
              </IconButton>
            </Stack>
          ))}
          <Autocomplete
            options={(users ?? []).filter((u) => !chosen.has(u.id))}
            getOptionLabel={(u) => u.displayName}
            value={null}
            onChange={(_e, u) =>
              u && setMembers((ms) => [...ms, { userId: u.id, role: ms.some((x) => x.role === 'CHAIR') ? 'MEMBER' : 'CHAIR' }])
            }
            renderInput={(params) => <TextField {...params} label="Add a person" size="small" />}
            blurOnSelect
            clearOnBlur
          />
          {issues.length > 0 && (
            <Alert severity="info">Before appointment: {issues.join('; ')}.</Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={save.isPending}>
          Save committee
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CommitteeDialog.propTypes = { tenderId: PropTypes.string.isRequired, evaluation: evalShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- committee change (after appointment)
export function CommitteeChangeDialog({ tenderId, evaluation, onClose }) {
  const { data: users } = useUsers();
  const [userId, setUserId] = useState(null);
  const [role, setRole] = useState('MEMBER');
  const [reason, setReason] = useState('');
  const change = useChangeCommittee(tenderId);
  const onCommittee = new Set(evaluation.members.map((m) => m.userId));
  const options = (users ?? []).filter((u) => role === 'CHAIR' || !onCommittee.has(u.id));

  const submit = (e) => {
    e.preventDefault();
    change.mutate({ userId, role, reason: reason.trim() }, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Change the appointed committee</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Add a member (for example to restore the quorum after a conflict of interest) or name a new chair. The
          change and its reason are recorded.
        </DialogContentText>
        <ErrorBox error={change.error} />
        <Stack spacing={2}>
          <TextField select label="Change" value={role} onChange={(e) => setRole(e.target.value)} fullWidth>
            <MenuItem value="MEMBER">Add a member</MenuItem>
            <MenuItem value="SECRETARY">Add the secretary</MenuItem>
            <MenuItem value="CHAIR">Name a new chair</MenuItem>
          </TextField>
          <Autocomplete
            options={options}
            getOptionLabel={(u) => u.displayName}
            value={(users ?? []).find((u) => u.id === userId) ?? null}
            onChange={(_e, u) => setUserId(u?.id ?? null)}
            renderInput={(params) => <TextField {...params} label="Person" required />}
          />
          <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline minRows={2} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!userId || !reason.trim()} loading={change.isPending}>
          Record change
        </Button>
      </DialogActions>
    </Dialog>
  );
}

CommitteeChangeDialog.propTypes = {
  tenderId: PropTypes.string.isRequired,
  evaluation: evalShape,
  onClose: PropTypes.func.isRequired,
};

// ------------------------------------------------------------------- declaration
export function DeclarationDialog({ tenderId, evaluation, onClose }) {
  const [conflict, setConflict] = useState(false);
  const [text, setText] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const declare = useDeclare(tenderId);
  const bidders = evaluation.bids.map((b) => b.supplier.name).join(', ');

  const submit = (e) => {
    e.preventDefault();
    declare.mutate({ conflict, declaration: text.trim() || null }, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Conflict of interest declaration</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          The bidders are: <strong>{bidders || 'none'}</strong>. Declare whether you, your family or a business
          you are connected with has any interest in any of them. You make this declaration once; it is part of
          the tender record.
        </DialogContentText>
        <ErrorBox error={declare.error} />
        <RadioGroup value={conflict ? 'yes' : 'no'} onChange={(e) => setConflict(e.target.value === 'yes')}>
          <FormControlLabel value="no" control={<Radio />} label="I have no conflict of interest with any bidder" />
          <FormControlLabel
            value="yes"
            control={<Radio />}
            label="I have a conflict of interest (I will take no further part in this evaluation)"
          />
        </RadioGroup>
        <TextField
          label={conflict ? 'Describe the conflict' : 'Comments (optional)'}
          value={text}
          onChange={(e) => setText(e.target.value)}
          required={conflict}
          multiline
          minRows={2}
          fullWidth
          sx={{ mt: 2 }}
        />
        <FormControlLabel
          sx={{ mt: 1 }}
          control={<Switch checked={confirmed} onChange={(e) => setConfirmed(e.target.checked)} />}
          label="I confirm this declaration is true"
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!confirmed || (conflict && !text.trim())} loading={declare.isPending}>
          Make declaration
        </Button>
      </DialogActions>
    </Dialog>
  );
}

DeclarationDialog.propTypes = { tenderId: PropTypes.string.isRequired, evaluation: evalShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- marks for one bid
export function MarksDialog({ tenderId, evaluation, bid, onClose }) {
  const stage = evaluation.status === 'TECHNICAL' ? 'TECHNICAL' : 'PRELIMINARY';
  const criteria = criteriaOf(evaluation.criteria, stage);
  const existing = marksByCriterion(evaluation.myMarks, bid.bidId);
  const [values, setValues] = useState(() =>
    Object.fromEntries(
      criteria.map((c) => {
        const m = existing[c.id];
        return [
          c.id,
          {
            passed: m?.passed ?? null,
            score: m?.score == null ? '' : String(Number(m.score)),
            comment: m?.comment ?? '',
          },
        ];
      }),
    ),
  );
  const save = useSaveMarks(tenderId);
  const set = (id, patch) => setValues((v) => ({ ...v, [id]: { ...v[id], ...patch } }));
  const complete = criteria.every((c) => {
    const v = values[c.id];
    return stage === 'PRELIMINARY'
      ? v.passed === true || (v.passed === false && v.comment.trim())
      : validScore(v.score, c.maxScore);
  });
  const total = stage === 'TECHNICAL' ? criteria.reduce((s, c) => s + (Number(values[c.id].score) || 0), 0) : null;
  const max = technicalTotal(evaluation.criteria);

  const submit = (e) => {
    e.preventDefault();
    save.mutate(
      {
        bidId: bid.bidId,
        marks: criteria.map((c) => {
          const v = values[c.id];
          return stage === 'PRELIMINARY'
            ? { criterionId: c.id, passed: v.passed, comment: v.comment.trim() || null }
            : { criterionId: c.id, score: Number(v.score), comment: v.comment.trim() || null };
        }),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>
        {bid.receiptNo} · {bid.supplier.name}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Your own {stage === 'PRELIMINARY' ? 'pass/fail marks' : 'scores'}. Other members cannot see them until the
          chair closes the stage. You can change them until then.
        </DialogContentText>
        <ErrorBox error={save.error} />
        <Stack spacing={2.5}>
          {criteria.map((c) => {
            const v = values[c.id];
            return (
              <Box key={c.id}>
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  spacing={1}
                  sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' } }}
                >
                  <Box sx={{ flex: 1 }}>
                    <Typography variant="body1">{c.name}</Typography>
                    {c.description && (
                      <Typography variant="caption" color="text.secondary">
                        {c.description}
                      </Typography>
                    )}
                  </Box>
                  {stage === 'PRELIMINARY' ? (
                    <ToggleButtonGroup
                      exclusive
                      size="small"
                      value={v.passed}
                      onChange={(_e, val) => val !== null && set(c.id, { passed: val })}
                    >
                      <ToggleButton value color="success">
                        Met
                      </ToggleButton>
                      <ToggleButton value={false} color="error">
                        Not met
                      </ToggleButton>
                    </ToggleButtonGroup>
                  ) : (
                    <TextField
                      size="small"
                      label={`Score / ${Number(c.maxScore)}`}
                      value={v.score}
                      onChange={(e) => set(c.id, { score: e.target.value })}
                      error={v.score !== '' && !validScore(v.score, c.maxScore)}
                      sx={{ width: 140 }}
                      slotProps={{ htmlInput: { inputMode: 'decimal' } }}
                    />
                  )}
                </Stack>
                {(stage === 'TECHNICAL' || v.passed === false || v.comment) && (
                  <TextField
                    size="small"
                    label={v.passed === false ? 'Why is it not met?' : 'Comment (optional)'}
                    value={v.comment}
                    onChange={(e) => set(c.id, { comment: e.target.value })}
                    required={v.passed === false}
                    fullWidth
                    sx={{ mt: 1 }}
                  />
                )}
              </Box>
            );
          })}
        </Stack>
        {total !== null && (
          <Typography variant="subtitle1" sx={{ mt: 3 }}>
            Your total: {Math.round(total * 100) / 100} / {max}
            {max ? ` (${Math.round((total / max) * 10000) / 100}%)` : ''} · pass mark {evaluation.passMarkPct}%
          </Typography>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={!complete} loading={save.isPending}>
          Save marks
        </Button>
      </DialogActions>
    </Dialog>
  );
}

MarksDialog.propTypes = {
  tenderId: PropTypes.string.isRequired,
  evaluation: evalShape,
  bid: PropTypes.object.isRequired,
  onClose: PropTypes.func.isRequired,
};

// ------------------------------------------------------------------- financial
export function PriceDialog({ tenderId, evaluation, bid, onClose }) {
  const [amount, setAmount] = useState(minorToInput(bid.evaluatedPriceMinor ?? bid.readOutPriceMinor));
  const [note, setNote] = useState(bid.priceNote ?? '');
  const save = useSavePrice(tenderId);
  const minor = inputToMinor(amount);
  const differs = bid.readOutPriceMinor != null && minor !== bid.readOutPriceMinor;
  const invalid = minor === null || Number.isNaN(minor) || (differs && !note.trim());

  const submit = (e) => {
    e.preventDefault();
    save.mutate({ bidId: bid.bidId, evaluatedPriceMinor: minor, note: note.trim() || null }, { onSuccess: onClose });
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Evaluated price · {bid.supplier.name}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Read out at opening:{' '}
          {bid.readOutPriceMinor != null ? formatMoney(bid.readOutPriceMinor, evaluation.currency) : 'not recorded'}. Enter
          the price after arithmetic corrections and any discounts the tender document allows.
        </DialogContentText>
        <ErrorBox error={save.error} />
        <Stack spacing={2}>
          <TextField
            label={`Evaluated price (${evaluation.currency})`}
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            required
            error={Number.isNaN(minor)}
            slotProps={{ htmlInput: { inputMode: 'decimal' } }}
          />
          <TextField
            label={differs ? 'What was corrected?' : 'Note (optional)'}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            required={differs}
            multiline
            minRows={2}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" disabled={invalid} loading={save.isPending}>
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

PriceDialog.propTypes = {
  tenderId: PropTypes.string.isRequired,
  evaluation: evalShape,
  bid: PropTypes.object.isRequired,
  onClose: PropTypes.func.isRequired,
};

export function RejectBidDialog({ tenderId, bid, onClose }) {
  const [reason, setReason] = useState('');
  const reject = useRejectBid(tenderId);
  const submit = (e) => {
    e.preventDefault();
    reject.mutate({ bidId: bid.bidId, reason: reason.trim() }, { onSuccess: onClose });
  };
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Reject {bid.supplier.name} at the financial stage</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          For example when the bidder does not accept the correction of an arithmetic error. This cannot be undone.
        </DialogContentText>
        <ErrorBox error={reject.error} />
        <TextField label="Reason" value={reason} onChange={(e) => setReason(e.target.value)} required multiline minRows={3} fullWidth />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" color="error" variant="contained" disabled={!reason.trim()} loading={reject.isPending}>
          Reject bid
        </Button>
      </DialogActions>
    </Dialog>
  );
}

RejectBidDialog.propTypes = { tenderId: PropTypes.string.isRequired, bid: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- report
export function ReportDialog({ tenderId, evaluation, onClose }) {
  const ranked = evaluation.bids
    .filter((b) => b.rank != null && !b.disqualifiedStage)
    .sort((a, b) => a.rank - b.rank);
  const [choice, setChoice] = useState(ranked[0]?.bidId ?? 'NONE');
  const [text, setText] = useState(evaluation.recommendation?.text ?? '');
  const [deviation, setDeviation] = useState('');
  const submit = useSubmitReport(tenderId);
  const chosen = ranked.find((b) => b.bidId === choice);
  const needsDeviation = chosen && chosen.rank !== 1;

  const send = (e) => {
    e.preventDefault();
    submit.mutate(
      {
        bidId: choice === 'NONE' ? null : choice,
        noAward: choice === 'NONE',
        recommendation: text.trim(),
        deviationReason: needsDeviation ? deviation.trim() : null,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={send}>
      <DialogTitle>Committee recommendation</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          The award normally goes to the lowest evaluated responsive bid. Once submitted, the signatories sign the
          report and it goes for professional opinion and approval.
        </DialogContentText>
        <ErrorBox error={submit.error} />
        <RadioGroup value={choice} onChange={(e) => setChoice(e.target.value)}>
          {ranked.map((b) => (
            <FormControlLabel
              key={b.bidId}
              value={b.bidId}
              control={<Radio />}
              label={`${b.rank}. ${b.supplier.name} · ${formatMoney(b.evaluatedPriceMinor, evaluation.currency)}`}
            />
          ))}
          <FormControlLabel
            value="NONE"
            control={<Radio />}
            label={ranked.length ? 'No award (terminate the tender)' : 'No award: no bid is responsive'}
          />
        </RadioGroup>
        <Stack spacing={2} sx={{ mt: 2 }}>
          {needsDeviation && (
            <TextField
              label="Why not the lowest evaluated bid?"
              value={deviation}
              onChange={(e) => setDeviation(e.target.value)}
              required
              multiline
              minRows={2}
            />
          )}
          <TextField
            label="Recommendation"
            value={text}
            onChange={(e) => setText(e.target.value)}
            required
            multiline
            minRows={4}
            helperText="Summarise the evaluation and the reason for the recommendation."
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          type="submit"
          variant="contained"
          disabled={!text.trim() || (needsDeviation && !deviation.trim())}
          loading={submit.isPending}
        >
          Submit report
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReportDialog.propTypes = { tenderId: PropTypes.string.isRequired, evaluation: evalShape, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- signature
export function SignDialog({ tenderId, onClose }) {
  const [note, setNote] = useState('');
  const sign = useSign(tenderId);
  const submit = (e) => {
    e.preventDefault();
    sign.mutate(note.trim() || null, { onSuccess: onClose });
  };
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>Sign the evaluation report</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Your signature confirms you took part in the evaluation as recorded. If you disagree with any part, say so
          below: your note is kept with your signature.
        </DialogContentText>
        <ErrorBox error={sign.error} />
        <TextField label="Note or dissent (optional)" value={note} onChange={(e) => setNote(e.target.value)} multiline minRows={3} fullWidth />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button type="submit" variant="contained" loading={sign.isPending}>
          Sign
        </Button>
      </DialogActions>
    </Dialog>
  );
}

SignDialog.propTypes = { tenderId: PropTypes.string.isRequired, onClose: PropTypes.func.isRequired };

// ------------------------------------------------------------------- approval decision
export function DecisionDialog({ tenderId, evaluation, onClose }) {
  const can = evaluation.permissions;
  const step = evaluation.approvals.find((a) => a.round === evaluation.round && a.status === 'PENDING');
  const [decision, setDecision] = useState('APPROVE');
  const [note, setNote] = useState('');
  const decide = useDecide(tenderId);
  const noteRequired = can.decisionIsOpinion || decision !== 'APPROVE';
  const rec = evaluation.recommendation;
  const winner = evaluation.bids.find((b) => b.bidId === rec?.bidId);

  const submit = (e) => {
    e.preventDefault();
    decide.mutate({ decision, note: note.trim() || null }, { onSuccess: onClose });
  };

  const approveLabel = can.decisionIsOpinion
    ? 'Concur and forward'
    : rec?.noAward
      ? 'Approve: terminate without award'
      : can.decisionIsFinal
        ? 'Approve the award'
        : 'Approve and forward';

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" component="form" onSubmit={submit}>
      <DialogTitle>
        {can.decisionIsOpinion ? 'Professional opinion' : 'Award decision'} ·{' '}
        {ROLE_LABEL[step?.approverRole] ?? step?.approverRole}
      </DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          {rec?.noAward
            ? 'The committee recommends no award.'
            : `The committee recommends ${winner?.supplier.name ?? 'a bidder'} at ${formatMoney(rec?.amountMinor, evaluation.currency)}.`}
        </DialogContentText>
        <ErrorBox error={decide.error} />
        <RadioGroup value={decision} onChange={(e) => setDecision(e.target.value)}>
          <FormControlLabel value="APPROVE" control={<Radio />} label={approveLabel} />
          <FormControlLabel value="RETURN" control={<Radio />} label="Return to the committee for review" />
          {can.decisionIsFinal && !rec?.noAward && (
            <FormControlLabel value="TERMINATE" control={<Radio />} label="Terminate the tender without award" />
          )}
        </RadioGroup>
        <TextField
          label={can.decisionIsOpinion && decision === 'APPROVE' ? 'Professional opinion' : noteRequired ? 'Reason' : 'Note (optional)'}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          required={noteRequired}
          multiline
          minRows={can.decisionIsOpinion ? 5 : 3}
          fullWidth
          sx={{ mt: 2 }}
          helperText={
            can.decisionIsOpinion
              ? 'Whether the procurement followed the law and the tender document, and whether you concur with the recommendation.'
              : undefined
          }
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Tooltip title={noteRequired && !note.trim() ? 'Write the note first' : ''}>
          <span>
            <Button
              type="submit"
              variant="contained"
              color={decision === 'APPROVE' ? 'primary' : 'warning'}
              disabled={noteRequired && !note.trim()}
              loading={decide.isPending}
            >
              Record decision
            </Button>
          </span>
        </Tooltip>
      </DialogActions>
    </Dialog>
  );
}

DecisionDialog.propTypes = { tenderId: PropTypes.string.isRequired, evaluation: evalShape, onClose: PropTypes.func.isRequired };
