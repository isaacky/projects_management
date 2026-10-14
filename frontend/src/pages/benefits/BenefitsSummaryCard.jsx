import { Link as RouterLink } from 'react-router';
import { Button, Card, CardContent, Stack, Typography } from '@mui/material';
import { useBenefitsPortfolio } from '../../api/benefits.js';
import { BenefitStats } from './BenefitsPage.jsx';

/** Dashboard: benefits realisation across the portfolio. */
export default function BenefitsSummaryCard() {
  const { data } = useBenefitsPortfolio();
  if (!data || data.summary.benefits === 0) return null;
  return (
    <Card sx={{ mt: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Typography variant="h6">Benefits realisation</Typography>
          <Button component={RouterLink} to="/benefits" size="small">
            All benefits
          </Button>
        </Stack>
        <BenefitStats summary={data.summary} />
      </CardContent>
    </Card>
  );
}
