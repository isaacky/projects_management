import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Button,
  Card,
  CardActions,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useDeleteTemplate, useSaveTemplate } from '../../api/procurementSettings.js';
import { criteriaOf, technicalTotal } from '../../domain/evaluation.js';
import {
  CriteriaEditor,
  criteriaRowsInvalid,
  criteriaRowsToBody,
  criterionRow,
} from '../tenders/evaluation/EvaluationDialogs.jsx';
import { methodShape } from './propTypes.js';

function TemplateDialog({ template, methods, onClose }) {
  const isNew = !template;
  const [code, setCode] = useState(template?.code ?? '');
  const [name, setName] = useState(template?.name ?? '');
  const [methodCode, setMethodCode] = useState(template?.methodCode ?? '');
  const [description, setDescription] = useState(template?.description ?? '');
  const [active, setActive] = useState(template?.active ?? true);
  const [rows, setRows] = useState(() =>
    template
      ? [...criteriaOf(template.criteria, 'PRELIMINARY'), ...criteriaOf(template.criteria, 'TECHNICAL')].map(criterionRow)
      : [criterionRow({ stage: 'PRELIMINARY', name: 'Valid KRA tax compliance certificate' })],
  );
  const save = useSaveTemplate();
  const codeOk = /^[A-Z][A-Z0-9_]{0,39}$/.test(code);

  const submit = (e) => {
    e.preventDefault();
    save.mutate(
      {
        code,
        name: name.trim(),
        methodCode: methodCode || null,
        description: description.trim() || null,
        active,
        criteria: criteriaRowsToBody(rows),
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md" component="form" onSubmit={submit}>
      <DialogTitle>{isNew ? 'New evaluation criteria template' : `Edit ${template.name}`}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Procurement staff start each evaluation from a template and can adjust it to the tender document before the
          committee is appointed. Changing a template never alters evaluations already set up.
        </DialogContentText>
        {save.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {save.error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mb: 3 }}>
          <Grid size={{ xs: 12, sm: 4 }}>
            <TextField
              label="Code"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '_'))}
              disabled={!isNew}
              required
              error={Boolean(code) && !codeOk}
              helperText="e.g. OPEN_TENDER_WORKS"
              fullWidth
            />
          </Grid>
          <Grid size={{ xs: 12, sm: 8 }}>
            <TextField label="Name" value={name} onChange={(e) => setName(e.target.value)} required fullWidth />
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <TextField select label="For method" value={methodCode} onChange={(e) => setMethodCode(e.target.value)} fullWidth>
              <MenuItem value="">Any method</MenuItem>
              {methods.map((m) => (
                <MenuItem key={m.code} value={m.code}>
                  {m.name}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 12, sm: 6 }}>
            <FormControlLabel
              control={<Switch checked={active} onChange={(e) => setActive(e.target.checked)} />}
              label="In use"
            />
          </Grid>
          <Grid size={12}>
            <TextField label="Description" value={description} onChange={(e) => setDescription(e.target.value)} fullWidth />
          </Grid>
        </Grid>
        <CriteriaEditor rows={rows} setRows={setRows} />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          type="submit"
          variant="contained"
          disabled={!codeOk || !name.trim() || criteriaRowsInvalid(rows)}
          loading={save.isPending}
        >
          Save template
        </Button>
      </DialogActions>
    </Dialog>
  );
}

TemplateDialog.propTypes = {
  template: PropTypes.object,
  methods: PropTypes.arrayOf(methodShape).isRequired,
  onClose: PropTypes.func.isRequired,
};

export default function TemplatesTab({ templates, methods }) {
  const [editing, setEditing] = useState(null);
  const remove = useDeleteTemplate();
  const methodName = (code) => methods.find((m) => m.code === code)?.name ?? code;

  return (
    <>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="body2" color="text.secondary">
          Preliminary criteria are pass/fail; technical criteria are scored and compared with the pass mark in the
          committee rule.
        </Typography>
        <Button startIcon={<AddIcon />} onClick={() => setEditing({ isNew: true })}>
          Add template
        </Button>
      </Stack>
      {remove.error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {remove.error.message}
        </Alert>
      )}
      <Grid container spacing={2}>
        {templates.map((t) => {
          const prelim = criteriaOf(t.criteria, 'PRELIMINARY');
          const tech = criteriaOf(t.criteria, 'TECHNICAL');
          return (
            <Grid key={t.code} size={{ xs: 12, md: 6 }}>
              <Card variant="outlined" sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                <CardContent sx={{ flex: 1 }}>
                  <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
                    <Typography variant="subtitle1">{t.name}</Typography>
                    <Chip size="small" label={t.methodCode ? methodName(t.methodCode) : 'Any method'} />
                    {!t.active && <Chip size="small" label="Not in use" />}
                  </Stack>
                  {t.description && (
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                      {t.description}
                    </Typography>
                  )}
                  <Typography variant="body2">
                    {prelim.length} preliminary criteria
                    {tech.length ? ` · ${tech.length} technical criteria out of ${technicalTotal(t.criteria)}` : ' · no technical scoring'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {t.code}
                  </Typography>
                </CardContent>
                <CardActions>
                  <Button size="small" onClick={() => setEditing({ template: t })}>
                    Edit
                  </Button>
                  <Button size="small" color="error" onClick={() => remove.mutate(t.code)}>
                    Remove
                  </Button>
                </CardActions>
              </Card>
            </Grid>
          );
        })}
      </Grid>
      {!templates.length && <Typography color="text.secondary">No templates yet.</Typography>}
      {editing && <TemplateDialog template={editing.template ?? null} methods={methods} onClose={() => setEditing(null)} />}
    </>
  );
}

TemplatesTab.propTypes = {
  templates: PropTypes.array.isRequired,
  methods: PropTypes.arrayOf(methodShape).isRequired,
};
