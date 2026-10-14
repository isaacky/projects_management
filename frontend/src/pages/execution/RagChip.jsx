import PropTypes from 'prop-types';
import { Chip } from '@mui/material';
import { RAG } from '../../domain/planning.js';

export default function RagChip({ rag, label }) {
  if (!rag) return <Chip size="small" variant="outlined" label={label ? `${label}: —` : '—'} />;
  return <Chip size="small" color={RAG[rag].color} label={label ? `${label}: ${RAG[rag].label}` : RAG[rag].label} />;
}

RagChip.propTypes = { rag: PropTypes.oneOf(['GREEN', 'AMBER', 'RED']), label: PropTypes.string };
