import PropTypes from 'prop-types';
import { Link as RouterLink, useParams } from 'react-router';
import {
  Alert,
  Box,
  Breadcrumbs,
  Button,
  GlobalStyles,
  Link,
  Paper,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import PrintOutlined from '@mui/icons-material/PrintOutlined';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useTender } from '../../api/tenders.js';
import { useEvaluation } from '../../api/evaluation.js';
import { useSuppliers } from '../../api/suppliers.js';
import { regretReason } from '../../domain/evaluation.js';
import { formatMoney } from '../../utils/money.js';

const printStyles = (
  <GlobalStyles
    styles={{
      '@media print': {
        'header, nav, .MuiDrawer-root, .no-print': { display: 'none !important' },
        'main, body': { margin: 0, padding: 0, background: '#fff' },
        '.letter': { boxShadow: 'none !important', border: 'none !important', breakAfter: 'page', margin: '0 !important' },
      },
    }}
  />
);

const today = () => new Date().toLocaleDateString('en-KE', { dateStyle: 'long' });

function Letter({ supplier, tender, subject, children }) {
  return (
    <Paper className="letter" variant="outlined" sx={{ p: { xs: 3, md: 6 }, mb: 4, maxWidth: 800, mx: 'auto' }}>
      <Typography variant="overline" color="text.secondary">
        [Letterhead]
      </Typography>
      <Typography sx={{ mt: 2 }}>{today()}</Typography>
      <Box sx={{ my: 3 }}>
        <Typography sx={{ fontWeight: 600 }}>{supplier?.name}</Typography>
        {supplier?.address && <Typography sx={{ whiteSpace: 'pre-line' }}>{supplier.address}</Typography>}
        {supplier?.email && <Typography>{supplier.email}</Typography>}
      </Box>
      <Typography sx={{ fontWeight: 700, mb: 2 }}>
        RE: {tender.number}: {tender.title.toUpperCase()} · {subject}
      </Typography>
      {children}
      <Box sx={{ mt: 6 }}>
        <Typography>Yours faithfully,</Typography>
        <Typography sx={{ mt: 6 }}>______________________________</Typography>
        <Typography>For the Accounting Officer</Typography>
      </Box>
    </Paper>
  );
}

Letter.propTypes = {
  supplier: PropTypes.object,
  tender: PropTypes.object.isRequired,
  subject: PropTypes.string.isRequired,
  children: PropTypes.node,
};

/** Award and regret letters, ready to print on letterhead. All bidders are notified at the same time. */
export default function LettersPage() {
  const { id } = useParams();
  const { data: tender, error, isLoading } = useTender(id);
  const { data: evalData } = useEvaluation(id);
  const { data: suppliers } = useSuppliers();

  if (error) return <ErrorAlert error={error} title="Could not load tender" />;
  if (isLoading || !tender || !evalData) return <Skeleton variant="rounded" height={320} />;
  const e = evalData.evaluation;
  if (!e || !['AWARDED', 'TERMINATED'].includes(e.status)) {
    return <Alert severity="info">Letters are available once the award decision is made.</Alert>;
  }

  const supplierById = Object.fromEntries((suppliers ?? []).map((s) => [s.id, s]));
  const winner = e.bids.find((b) => b.bidId === e.award?.bidId);
  const standstill = e.award?.standstillEndsAt
    ? new Date(e.award.standstillEndsAt).toLocaleDateString('en-KE', { dateStyle: 'long' })
    : null;
  const others = e.bids.filter((b) => b.bidId !== winner?.bidId);

  return (
    <>
      {printStyles}
      <Box className="no-print">
        <Breadcrumbs sx={{ mb: 1 }}>
          <Link component={RouterLink} to="/tenders" underline="hover" color="inherit">
            Tenders
          </Link>
          <Link component={RouterLink} to={`/tenders/${id}?tab=evaluation`} underline="hover" color="inherit">
            {tender.number}
          </Link>
          <Typography color="text.primary">Notification letters</Typography>
        </Breadcrumbs>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h5">Notification letters</Typography>
          <Button variant="contained" startIcon={<PrintOutlined />} onClick={() => window.print()}>
            Print
          </Button>
        </Stack>
        <Alert severity="info" sx={{ mb: 3 }}>
          Drafts generated from the evaluation record. Check the wording against your standard letters, print on
          letterhead and send all letters at the same time.
        </Alert>
      </Box>

      {winner && (
        <Letter supplier={supplierById[winner.supplier.id]} tender={tender} subject="NOTIFICATION OF AWARD">
          <Typography paragraph>
            We are pleased to notify you that your bid (receipt {winner.receiptNo}) for the above tender has been
            accepted at an evaluated price of <strong>{formatMoney(e.award.amountMinor, e.currency)}</strong>.
          </Typography>
          <Typography paragraph>
            The contract will be signed after the standstill period ends on <strong>{standstill}</strong>, unless a
            request for review is filed with the Public Procurement Administrative Review Board within that period.
          </Typography>
          <Typography paragraph>Please confirm your acceptance of this award in writing.</Typography>
        </Letter>
      )}

      {others.map((b) => (
        <Letter
          key={b.bidId}
          supplier={supplierById[b.supplier.id]}
          tender={tender}
          subject={winner ? 'NOTIFICATION OF UNSUCCESSFUL BID' : 'NOTIFICATION OF TERMINATION'}
        >
          <Typography paragraph>
            Thank you for your bid (receipt {b.receiptNo}) for the above tender. We regret to inform you that your bid
            was not successful.
          </Typography>
          <Typography paragraph>{regretReason(b, winner, e.currency, formatMoney)}</Typography>
          {winner ? (
            <Typography paragraph>
              The tender was awarded to <strong>{winner.supplier.name}</strong> at an evaluated price of{' '}
              <strong>{formatMoney(e.award.amountMinor, e.currency)}</strong>.
            </Typography>
          ) : (
            <Typography paragraph>The tender was terminated: {tender.cancelledReason}</Typography>
          )}
          {standstill && (
            <Typography paragraph>
              Any request for review may be made to the Public Procurement Administrative Review Board before{' '}
              {standstill}.
            </Typography>
          )}
        </Letter>
      ))}
      {winner && e.bids.length === 1 && (
        <Typography color="text.secondary" className="no-print">
          There were no other bidders.
        </Typography>
      )}
    </>
  );
}
