import PropTypes from 'prop-types';
import { Link as RouterLink } from 'react-router';
import { Box, Card, CardContent, LinearProgress, Link, List, ListItem, ListItemText, Stack, Typography } from '@mui/material';
import ContractStatusChip from './ContractStatusChip.jsx';
import { useContracts } from '../../api/contracts.js';
import { portfolioTotals, share } from '../../domain/contracts.js';
import { formatMoney } from '../../utils/money.js';

/** A project's contracts and how much of its budget is committed, certified and paid. */
export default function ProjectContracts({ project }) {
  const { data } = useContracts({ projectId: project.id });
  const t = portfolioTotals(data);
  const budget = project.budgetMinor;
  const bar = (label, value) => (
    <Box sx={{ mb: 1 }}>
      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
        <Typography variant="body2">{label}</Typography>
        <Typography variant="body2">
          {formatMoney(value, project.currency)} · {share(value, budget)}% of budget
        </Typography>
      </Stack>
      <LinearProgress
        variant="determinate"
        value={Math.min(100, share(value, budget))}
        color={value > budget ? 'error' : 'primary'}
      />
    </Box>
  );
  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Typography variant="h6" sx={{ mb: 1 }}>
          Contracts
        </Typography>
        {t.count > 0 && (
          <Box sx={{ mb: 2 }}>
            {bar('Committed', t.committed)}
            {bar('Certified', t.certified)}
            {bar('Paid', t.paid)}
          </Box>
        )}
        {!data?.length && (
          <Typography variant="body2" color="text.secondary">
            None yet.
          </Typography>
        )}
        <List dense>
          {data?.map((c) => (
            <ListItem key={c.id} disableGutters divider secondaryAction={<ContractStatusChip status={c.status} />}>
              <ListItemText
                primary={
                  <Link component={RouterLink} to={`/contracts/${c.id}`}>
                    {c.number} · {c.title}
                  </Link>
                }
                secondary={`${c.supplier.name} · ${formatMoney(c.currentValueMinor, c.currency)} · paid ${formatMoney(c.paidMinor, c.currency)}`}
              />
            </ListItem>
          ))}
        </List>
      </CardContent>
    </Card>
  );
}

ProjectContracts.propTypes = { project: PropTypes.object.isRequired };
