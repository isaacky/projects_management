import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { TENDER_STATUS, displayStatus } from '../../domain/tenders.js';

export default function TenderStatusChip({ tender, size = 'small' }) {
  const s = TENDER_STATUS[displayStatus(tender)] ?? { label: tender.status, color: 'default' };
  return <Chip size={size} label={s.label} color={s.color} variant="outlined" />;
}

TenderStatusChip.propTypes = {
  tender: PropTypes.shape({ status: PropTypes.string.isRequired, closed: PropTypes.bool }).isRequired,
  size: PropTypes.oneOf(['small', 'medium']),
};
