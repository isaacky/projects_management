import { useState } from 'react';
import PropTypes from 'prop-types';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined';
import { useImportSuppliers } from '../../api/suppliers.js';
import { downloadFile } from '../../api/client.js';

const FIELD_LABELS = {
  NAME: 'Supplier name',
  KRA_PIN: 'KRA PIN',
  REGISTRATION_NO: 'Registration no.',
  EMAIL: 'Email',
  PHONE: 'Phone',
  ADDRESS: 'Address',
  CATEGORIES: 'Categories',
  AGPO_CATEGORY: 'AGPO category',
  AGPO_CERTIFICATE_NO: 'AGPO certificate',
  AGPO_EXPIRY: 'AGPO expiry',
  STATUS: 'Status',
};

const ACTION_CHIP = {
  CREATE: { label: 'New', color: 'success' },
  UPDATE: { label: 'Update', color: 'info' },
  SKIP: { label: 'Skipped', color: 'error' },
};

/** Two steps: choose a CSV and preview what will happen, then import. */
export default function ImportDialog({ onClose }) {
  const [file, setFile] = useState(null);
  const run = useImportSuppliers();
  const result = run.data;
  const previewed = result && !result.applied;
  const done = result?.applied;

  const choose = (e) => {
    const f = e.target.files?.[0];
    e.target.value = '';
    if (!f) return;
    setFile(f);
    run.reset();
    run.mutate({ file: f, apply: false });
  };

  const rows = result?.rows ?? [];
  const problemRows = rows.filter((r) => r.errors.length || r.warnings.length);

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Import suppliers from CSV</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>
          Save the supplier list as CSV (in Excel: File &gt; Save As &gt; CSV UTF-8). Columns are matched by
          name, so &quot;Supplier Name&quot;, &quot;KRA PIN&quot;, &quot;Email&quot; and similar headings work.
          Suppliers already registered are updated by KRA PIN. Nothing is saved until you confirm.{' '}
          <Link component="button" type="button" onClick={() => downloadFile('/api/v1/suppliers/import/template')}>
            Download a template
          </Link>
          .
        </DialogContentText>

        <Button component="label" variant="outlined" startIcon={<UploadFileOutlined />} disabled={run.isPending}>
          {file ? 'Choose another file' : 'Choose CSV file'}
          <input hidden type="file" accept=".csv,text/csv" onChange={choose} />
        </Button>
        {file && (
          <Typography variant="body2" color="text.secondary" sx={{ ml: 2, display: 'inline' }}>
            {file.name}
          </Typography>
        )}

        {run.error && (
          <Alert severity="error" sx={{ mt: 2 }}>
            {run.error.message}
          </Alert>
        )}
        {result?.fileErrors?.map((e) => (
          <Alert key={e} severity="error" sx={{ mt: 2 }}>
            {e}
          </Alert>
        ))}

        {result && !result.fileErrors.length && (
          <Box sx={{ mt: 3 }}>
            {done ? (
              <Alert severity="success" sx={{ mb: 2 }}>
                Imported: {result.toCreate} new, {result.toUpdate} updated, {result.skipped} skipped.
              </Alert>
            ) : (
              <Alert severity={result.skipped ? 'warning' : 'info'} sx={{ mb: 2 }}>
                Ready to import: {result.toCreate} new, {result.toUpdate} updates
                {result.skipped ? `, ${result.skipped} rows will be skipped because of errors` : ''}.
              </Alert>
            )}
            <Typography variant="subtitle2" gutterBottom>
              Columns recognised
            </Typography>
            <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mb: 2 }}>
              {Object.entries(result.columns).map(([field, header]) => (
                <Chip key={field} size="small" label={`${header} → ${FIELD_LABELS[field] ?? field}`} />
              ))}
              {result.unmappedColumns.map((h) => (
                <Chip key={h} size="small" variant="outlined" label={`${h} (ignored)`} />
              ))}
            </Stack>

            {problemRows.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>
                  Rows needing attention ({problemRows.length})
                </Typography>
                <TableContainer sx={{ maxHeight: 320 }}>
                  <Table size="small" stickyHeader>
                    <TableHead>
                      <TableRow>
                        <TableCell>Line</TableCell>
                        <TableCell>Supplier</TableCell>
                        <TableCell>Result</TableCell>
                        <TableCell>Details</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {problemRows.slice(0, 500).map((r) => (
                        <TableRow key={r.line}>
                          <TableCell>{r.line}</TableCell>
                          <TableCell>
                            {r.values.NAME ?? '—'}
                            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                              {r.values.KRA_PIN}
                            </Typography>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" {...ACTION_CHIP[r.action]} variant="outlined" />
                          </TableCell>
                          <TableCell>
                            {r.errors.map((e) => (
                              <Typography key={e} variant="body2" color="error">
                                {e}
                              </Typography>
                            ))}
                            {r.warnings.map((w) => (
                              <Typography key={w} variant="body2" color="warning.main">
                                {w}
                              </Typography>
                            ))}
                          </TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </>
            )}
          </Box>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>{done ? 'Close' : 'Cancel'}</Button>
        {previewed && !result.fileErrors.length && (
          <Button
            variant="contained"
            disabled={!(result.toCreate + result.toUpdate)}
            loading={run.isPending}
            onClick={() => run.mutate({ file, apply: true })}
          >
            Import {result.toCreate + result.toUpdate} suppliers
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

ImportDialog.propTypes = {
  onClose: PropTypes.func.isRequired,
};
