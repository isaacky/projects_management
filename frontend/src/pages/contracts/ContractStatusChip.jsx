import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { CONTRACT_STATUS } from '../../domain/contracts.js';

export default function ContractStatusChip({ status }) {
  const s = CONTRACT_STATUS[status] ?? { label: status, color: 'default' };
  return <Chip size="small" label={s.label} color={s.color} />;
}

ContractStatusChip.propTypes = { status: PropTypes.string.isRequired };
