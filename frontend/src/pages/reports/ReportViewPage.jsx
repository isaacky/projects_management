import { useMemo, useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  Card,
  CardContent,
  GlobalStyles,
  Link,
  MenuItem,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { DataGrid } from '@mui/x-data-grid';
import FileDownloadOutlined from '@mui/icons-material/FileDownloadOutlined';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { downloadReport, useReport, useReportCatalogue, useReportFilters } from '../../api/reports.js';
import { csvRows, defaultParams, formatCell, isNumeric } from '../../domain/reports.js';
import { saveText, toCsv } from '../../domain/procurementPlan.js';
import { formatDateTime } from '../../domain/tenders.js';

/** When printing, show only the report: hide the app's bar and menu and the on-screen grid. */
const printStyles = (
  <GlobalStyles
    styles={{
      '@media print': {
        'header, nav, .MuiDrawer-root, .MuiAppBar-root, .no-print': { display: 'none !important' },
        main: { margin: '0 !important', padding: '0 !important', width: '100% !important' },
        '.print-only': { display: 'block !important' },
        '@page': { size: 'landscape', margin: '12mm' },
      },
      '.print-only': { display: 'none' },
    }}
  />
);

function Filters({ filters, params, onChange }) {
  return (
    <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
      {filters.map((f) =>
        f.type === 'DATE' ? (
          <TextField
            key={f.key}
            size="small"
            type="date"
            label={f.label}
            value={params[f.key] ?? ''}
            onChange={(e) => onChange(f.key, e.target.value)}
            slotProps={{ inputLabel: { shrink: true } }}
          />
        ) : (
          <TextField
            key={f.key}
            select
            size="small"
            label={f.label}
            value={params[f.key] ?? ''}
            onChange={(e) => onChange(f.key, e.target.value)}
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            sx={{ minWidth: 200 }}
          >
            {f.options.map((o) => (
              <MenuItem key={o.value} value={o.value}>
                {o.label}
              </MenuItem>
            ))}
          </TextField>
        ),
      )}
    </Stack>
  );
}

Filters.propTypes = {
  filters: PropTypes.array.isRequired,
  params: PropTypes.object.isRequired,
  onChange: PropTypes.func.isRequired,
};

/** A plain table for printing (the on-screen grid pages and scrolls, which does not print well). */
function PrintTable({ result }) {
  const cell = { border: '1px solid #ccc', padding: '3px 6px', fontSize: 10 };
  return (
    <Box className="print-only">
      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
        <thead>
          <tr>
            {result.columns.map((c) => (
              <th key={c.key} style={{ ...cell, background: '#e8eef4', textAlign: isNumeric(c.type) ? 'right' : 'left' }}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {result.rows.map((r, i) => (
            <tr key={i}>
              {result.columns.map((c) => (
                <td key={c.key} style={{ ...cell, textAlign: isNumeric(c.type) ? 'right' : 'left' }}>
                  {formatCell(c.type, r[c.key], r._currency ?? result.currency)}
                </td>
              ))}
            </tr>
          ))}
          {Object.keys(result.totals ?? {}).length > 0 && (
            <tr>
              {result.columns.map((c, i) => (
                <td key={c.key} style={{ ...cell, fontWeight: 700, textAlign: isNumeric(c.type) ? 'right' : 'left' }}>
                  {i === 0 && result.totals[c.key] == null ? 'Total' : formatCell(c.type, result.totals[c.key], result.currency)}
                </td>
              ))}
            </tr>
          )}
        </tbody>
      </table>
    </Box>
  );
}

PrintTable.propTypes = { result: PropTypes.object.isRequired };

function Totals({ result }) {
  const shown = result.columns.filter((c) => result.totals?.[c.key] != null);
  if (!shown.length) return null;
  return (
    <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1, mt: 2 }} className="no-print">
      {shown.map((c) => (
        <Box key={c.key}>
          <Typography variant="overline" color="text.secondary">
            {c.label}
          </Typography>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
            {formatCell(c.type, result.totals[c.key], result.currency)}
          </Typography>
        </Box>
      ))}
    </Stack>
  );
}

Totals.propTypes = { result: PropTypes.object.isRequired };

function ReportView({ info }) {
  const navigate = useNavigate();
  const [params, setParams] = useState(() => defaultParams(info.filters));
  const { data: filters } = useReportFilters(info.id, params);
  const { data: result, isFetching, error } = useReport(info.id, params);
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState(null);

  const change = (key, value) => {
    setParams((p) => {
      const next = { ...p, [key]: value };
      // Choosing another plan resets the filters that depend on it.
      if (key === 'planId') next.departmentId = '';
      return next;
    });
  };

  const columns = useMemo(
    () =>
      (result?.columns ?? []).map((c) => ({
        field: c.key,
        headerName: c.label,
        type: isNumeric(c.type) ? 'number' : 'string',
        minWidth: c.type === 'TEXT' ? 140 : 110,
        flex: c.key === 'title' || c.key === 'description' || c.key === 'name' ? 1 : undefined,
        valueFormatter: (v, row) => formatCell(c.type, v, row._currency ?? result.currency),
        // Sort dates by value, not by their formatted text.
        sortComparator: c.type === 'DATE' ? (a, b) => String(a ?? '').localeCompare(String(b ?? '')) : undefined,
      })),
    [result],
  );
  const rows = useMemo(() => (result?.rows ?? []).map((r, i) => ({ id: i, ...r })), [result]);

  const excel = async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      await downloadReport(info.id, params);
    } catch (e) {
      setDownloadError(e);
    } finally {
      setDownloading(false);
    }
  };

  return (
    <>
      {printStyles}
      <Box className="no-print">
        <Breadcrumbs sx={{ mb: 1 }}>
          <Link component={RouterLink} to="/reports" underline="hover" color="inherit">
            Reports
          </Link>
          <Typography color="text.primary">{info.title}</Typography>
        </Breadcrumbs>
      </Box>
      <PageHeader
        title={info.title}
        subtitle={result ? `${result.subtitle ?? ''}${result.subtitle ? ' · ' : ''}generated ${formatDateTime(result.generatedAt)}` : info.description}
        actions={
          <Stack direction="row" spacing={1} className="no-print">
            <Button startIcon={<FileDownloadOutlined />} variant="contained" loading={downloading} disabled={!result} onClick={excel}>
              Excel
            </Button>
            <Button
              disabled={!result}
              onClick={() => saveText(toCsv(csvRows(result)), `${info.id}.csv`)}
            >
              CSV
            </Button>
            <Button startIcon={<PrintOutlined />} disabled={!result} onClick={() => window.print()}>
              Print / PDF
            </Button>
          </Stack>
        }
      />
      {(filters ?? info.filters).length > 0 && (
        <Card sx={{ mb: 2 }} className="no-print">
          <CardContent>
            <Filters filters={filters ?? info.filters} params={params} onChange={change} />
          </CardContent>
        </Card>
      )}
      <ErrorAlert error={error} title="Could not run the report" />
      {downloadError && (
        <Alert severity="error" sx={{ mb: 2 }} className="no-print">
          {downloadError.message}
        </Alert>
      )}
      {!result && !error && <Skeleton variant="rounded" height={300} />}
      {result && (
        <>
          <Card className="no-print">
            <DataGrid
              rows={rows}
              columns={columns}
              loading={isFetching}
              autoHeight
              density="compact"
              disableRowSelectionOnClick
              onRowClick={({ row }) => row._link && navigate(row._link)}
              initialState={{ pagination: { paginationModel: { pageSize: 50 } } }}
              pageSizeOptions={[25, 50, 100]}
              localeText={{ noRowsLabel: 'Nothing matches these filters' }}
              sx={{ border: 0, '& .MuiDataGrid-row': { cursor: rows.some((r) => r._link) ? 'pointer' : 'default' } }}
            />
          </Card>
          <Totals result={result} />
          <PrintTable result={result} />
          {result.notes?.map((n) => (
            <Typography key={n} variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {n}
            </Typography>
          ))}
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
            {result.rows.length} row{result.rows.length === 1 ? '' : 's'}
          </Typography>
        </>
      )}
    </>
  );
}

ReportView.propTypes = { info: PropTypes.object.isRequired };

export default function ReportViewPage() {
  const { id } = useParams();
  const { data, isLoading, error } = useReportCatalogue();
  if (error) return <ErrorAlert error={error} title="Could not load the reports" />;
  if (isLoading || !data) return <Skeleton variant="rounded" height={300} />;
  const info = data.find((r) => r.id === id);
  if (!info) return <Alert severity="warning">There is no report called {id}.</Alert>;
  return <ReportView key={info.id} info={info} />;
}
