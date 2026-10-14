import PropTypes from 'prop-types';
import { Box, Stack, Typography, useTheme } from '@mui/material';
import { daysBetween } from '../../domain/planning.js';

const W = 640;
const H = 220;
const PAD = { left: 40, right: 12, top: 10, bottom: 26 };

/** Planned % over time (from the baseline) against earned % recorded at each progress update. */
export default function SCurve({ planned, actual, today }) {
  const theme = useTheme();
  if (!planned.length) return null;
  const first = planned[0].date;
  const last = planned[planned.length - 1].date;
  const span = Math.max(daysBetween(first, last), 1);
  const x = (d) => PAD.left + (Math.min(Math.max(daysBetween(first, d), 0), span) / span) * (W - PAD.left - PAD.right);
  const y = (v) => PAD.top + (1 - Number(v) / 100) * (H - PAD.top - PAD.bottom);
  const line = (points, key) => points.map((p, i) => `${i ? 'L' : 'M'}${x(p.date)},${y(p[key])}`).join(' ');
  const yearMonth = (d) => new Date(`${d}T00:00:00`).toLocaleDateString('en-KE', { month: 'short', year: '2-digit' });

  return (
    <Box>
      <svg viewBox={`0 0 ${W} ${H}`} width="100%" role="img" aria-label="Planned and earned progress over time">
        {[0, 25, 50, 75, 100].map((v) => (
          <g key={v}>
            <line x1={PAD.left} x2={W - PAD.right} y1={y(v)} y2={y(v)} stroke={theme.palette.divider} />
            <text x={PAD.left - 6} y={y(v) + 4} fontSize="11" textAnchor="end" fill={theme.palette.text.secondary}>
              {v}%
            </text>
          </g>
        ))}
        <text x={PAD.left} y={H - 6} fontSize="11" fill={theme.palette.text.secondary}>{yearMonth(first)}</text>
        <text x={W - PAD.right} y={H - 6} fontSize="11" textAnchor="end" fill={theme.palette.text.secondary}>{yearMonth(last)}</text>
        {today >= first && today <= last && (
          <line x1={x(today)} x2={x(today)} y1={PAD.top} y2={H - PAD.bottom} stroke={theme.palette.warning.main} strokeDasharray="4 3" />
        )}
        <path d={line(planned, 'planned')} fill="none" stroke={theme.palette.primary.main} strokeWidth={2} />
        {actual.length > 0 && (
          <>
            <path d={line(actual, 'earned')} fill="none" stroke={theme.palette.success.main} strokeWidth={2.5} />
            {actual.map((p) => (
              <circle key={p.date} cx={x(p.date)} cy={y(p.earned)} r={3.5} fill={theme.palette.success.main} />
            ))}
          </>
        )}
      </svg>
      <Stack direction="row" spacing={3} sx={{ justifyContent: 'center' }}>
        <Typography variant="caption" sx={{ color: 'primary.main' }}>— Planned (baseline)</Typography>
        <Typography variant="caption" sx={{ color: 'success.main' }}>● Earned (recorded progress)</Typography>
        <Typography variant="caption" sx={{ color: 'warning.main' }}>┆ Today</Typography>
      </Stack>
    </Box>
  );
}

SCurve.propTypes = {
  planned: PropTypes.arrayOf(PropTypes.object).isRequired,
  actual: PropTypes.arrayOf(PropTypes.object).isRequired,
  today: PropTypes.string.isRequired,
};
