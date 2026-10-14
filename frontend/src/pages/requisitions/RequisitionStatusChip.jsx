import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { REQUISITION_STATUS } from '../../domain/requisitions.js';

export default function RequisitionStatusChip({ status, size = 'small' }) {
  const s = REQUISITION_STATUS[status] ?? { label: status, color: 'default' };
  return <Chip size={size} label={s.label} color={s.color} variant="outlined" />;
}

RequisitionStatusChip.propTypes = {
  status: PropTypes.string.isRequired,
  size: PropTypes.oneOf(['small', 'medium']),
};
