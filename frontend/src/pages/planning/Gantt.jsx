import PropTypes from 'prop-types';
import { Box, Tooltip, Typography, useTheme } from '@mui/material';
import { addDays, daysBetween } from '../../domain/planning.js';
import { formatDate } from '../../domain/contracts.js';

const ROW = 30;
const LEFT = 470;

function span(a) {
  const dates = [a.start, a.finish, a.baselineStart, a.baselineFinish, a.forecastStart, a.forecastFinish].filter(Boolean);
  return dates;
}

/**
 * A read-only Gantt chart: planned bars (critical in red) with progress, baseline underneath,
 * forecast outline when it runs later, milestones as diamonds, and a line for today.
 */
export default function Gantt({ activities, today, onRowClick }) {
  const theme = useTheme();
  const all = activities.flatMap(span);
  if (!all.length) return null;
  const first = addDays(all.reduce((a, b) => (a < b ? a : b)), -2);
  const last = addDays(all.reduce((a, b) => (a > b ? a : b)), 3);
  const days = Math.max(daysBetween(first, last), 7);
  const px = Math.max(3, Math.min(28, Math.floor(900 / days)));
  const width = days * px;
  const x = (iso) => daysBetween(first, iso) * px;

  // Month ticks.
  const ticks = [];
  for (let d = first; d <= last; d = addDays(d, 1)) {
    if (d.endsWith('-01') || d === first) ticks.push(d);
  }
  const colors = {
    bar: theme.palette.primary.main,
    critical: theme.palette.error.main,
    baseline: theme.palette.text.disabled,
    summary: theme.palette.text.primary,
    today: theme.palette.warning.main,
    grid: theme.palette.divider,
  };

  return (
    <Box sx={{ overflowX: 'auto', border: 1, borderColor: 'divider', borderRadius: 1 }}>
      <Box sx={{ minWidth: LEFT + width }}>
        {/* header */}
        <Box sx={{ display: 'flex', height: ROW, borderBottom: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Box sx={{ width: LEFT, flexShrink: 0, display: 'flex', alignItems: 'center', px: 1, gap: 1, position: 'sticky', left: 0, bgcolor: 'background.paper', zIndex: 1 }}>
            <Typography variant="caption" sx={{ width: 34 }}>#</Typography>
            <Typography variant="caption" sx={{ width: 42 }}>WBS</Typography>
            <Typography variant="caption" sx={{ flex: 1 }}>Activity</Typography>
            <Typography variant="caption" sx={{ width: 40, textAlign: 'right' }}>Days</Typography>
            <Typography variant="caption" sx={{ width: 84 }}>Finish</Typography>
            <Typography variant="caption" sx={{ width: 40, textAlign: 'right' }}>%</Typography>
          </Box>
          <svg width={width} height={ROW}>
            {ticks.map((t) => (
              <g key={t}>
                <line x1={x(t)} x2={x(t)} y1={0} y2={ROW} stroke={colors.grid} />
                <text x={x(t) + 3} y={19} fontSize="11" fill={theme.palette.text.secondary}>
                  {new Date(`${t}T00:00:00`).toLocaleDateString('en-KE', { month: 'short', year: '2-digit' })}
                </text>
              </g>
            ))}
          </svg>
        </Box>
        {activities.map((a, i) => {
          const color = a.critical ? colors.critical : colors.bar;
          const milestone = !a.summary && a.durationDays === 0;
          const late = a.forecastFinish && a.finish && a.forecastFinish > a.finish;
          return (
            <Box
              key={a.id}
              onClick={onRowClick ? () => onRowClick(a) : undefined}
              sx={{
                display: 'flex',
                height: ROW,
                borderBottom: 1,
                borderColor: 'divider',
                cursor: onRowClick ? 'pointer' : 'default',
                '&:hover': onRowClick ? { bgcolor: 'action.hover' } : undefined,
              }}
            >
              <Box sx={{ width: LEFT, flexShrink: 0, display: 'flex', alignItems: 'center', px: 1, gap: 1, position: 'sticky', left: 0, bgcolor: 'background.paper', zIndex: 1 }}>
                <Typography variant="caption" color="text.secondary" sx={{ width: 34 }}>{i + 1}</Typography>
                <Typography variant="caption" sx={{ width: 42 }}>{a.wbs}</Typography>
                <Typography
                  variant="body2"
                  noWrap
                  title={a.name}
                  sx={{ flex: 1, pl: a.depth * 1.5, fontWeight: a.summary ? 600 : 400, color: a.late ? 'error.main' : undefined }}
                >
                  {a.name}
                </Typography>
                <Typography variant="caption" sx={{ width: 40, textAlign: 'right' }}>{a.summary ? '' : a.durationDays}</Typography>
                <Typography variant="caption" sx={{ width: 84 }}>{formatDate(a.finish)}</Typography>
                <Typography variant="caption" sx={{ width: 40, textAlign: 'right' }}>{Math.round(Number(a.percent))}</Typography>
              </Box>
              <Tooltip
                followCursor
                title={`${a.name}: ${formatDate(a.start)} to ${formatDate(a.finish)}${a.baselineFinish ? ` · baseline finish ${formatDate(a.baselineFinish)}` : ''}${late ? ` · forecast finish ${formatDate(a.forecastFinish)}` : ''} · float ${a.totalFloat} d · ${Math.round(Number(a.percent))}% done`}
              >
                <svg width={width} height={ROW} role="img" aria-label={a.name}>
                  {ticks.map((t) => (
                    <line key={t} x1={x(t)} x2={x(t)} y1={0} y2={ROW} stroke={colors.grid} />
                  ))}
                  {a.baselineStart && !a.summary && (
                    <rect x={x(a.baselineStart)} y={23} width={Math.max(2, (daysBetween(a.baselineStart, a.baselineFinish) + 1) * px)} height={3} fill={colors.baseline} />
                  )}
                  {a.summary ? (
                    <g fill={colors.summary}>
                      <rect x={x(a.start)} y={9} width={Math.max(2, (daysBetween(a.start, a.finish) + 1) * px)} height={5} />
                      <rect x={x(a.start)} y={9} width={Math.max(2, ((daysBetween(a.start, a.finish) + 1) * px * Number(a.percent)) / 100)} height={5} fill={theme.palette.success.main} />
                    </g>
                  ) : milestone ? (
                    <polygon
                      points={`${x(a.start)},${ROW / 2 - 7} ${x(a.start) + 7},${ROW / 2} ${x(a.start)},${ROW / 2 + 7} ${x(a.start) - 7},${ROW / 2}`}
                      fill={Number(a.percent) >= 100 ? theme.palette.success.main : color}
                    />
                  ) : (
                    <g>
                      {late && (
                        <rect
                          x={x(a.forecastStart)}
                          y={6}
                          width={Math.max(2, (daysBetween(a.forecastStart, a.forecastFinish) + 1) * px)}
                          height={14}
                          fill="none"
                          stroke={colors.critical}
                          strokeDasharray="3 2"
                          rx={2}
                        />
                      )}
                      <rect x={x(a.start)} y={7} width={Math.max(2, (daysBetween(a.start, a.finish) + 1) * px)} height={12} fill={color} opacity={0.35} rx={2} />
                      <rect x={x(a.start)} y={7} width={Math.max(0, ((daysBetween(a.start, a.finish) + 1) * px * Number(a.percent)) / 100)} height={12} fill={color} rx={2} />
                    </g>
                  )}
                  {today >= first && today <= last && (
                    <line x1={x(today)} x2={x(today)} y1={0} y2={ROW} stroke={colors.today} strokeWidth={2} />
                  )}
                </svg>
              </Tooltip>
            </Box>
          );
        })}
      </Box>
    </Box>
  );
}

Gantt.propTypes = {
  activities: PropTypes.arrayOf(PropTypes.object).isRequired,
  today: PropTypes.string.isRequired,
  onRowClick: PropTypes.func,
};
