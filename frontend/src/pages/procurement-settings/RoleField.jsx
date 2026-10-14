import { useState } from 'react';
import PropTypes from 'prop-types';
import { Autocomplete, TextField } from '@mui/material';
import { roleLabel, toRoleCode } from '../../domain/procurement.js';

// Common roles in public-sector procurement. Users can type any other role.
export const SUGGESTED_ROLES = [
  'HEAD_OF_DEPARTMENT',
  'HEAD_OF_PROCUREMENT',
  'FINANCE_MANAGER',
  'ACCOUNTING_OFFICER',
  'TENDER_COMMITTEE',
  'BOARD',
];

/**
 * Role picker: suggests common roles, accepts any typed role, and reports the role
 * code (e.g. "Accounting officer" -> ACCOUNTING_OFFICER) through onChange.
 */
export default function RoleField({ label, value, onChange, required = false, error, helperText }) {
  // The typed text is kept as typed; only the reported value is normalised.
  const [input, setInput] = useState(value ? roleLabel(value) : '');

  return (
    <Autocomplete
      freeSolo
      options={SUGGESTED_ROLES}
      getOptionLabel={(code) => roleLabel(code)}
      value={value || null}
      inputValue={input}
      onInputChange={(_e, text) => {
        setInput(text);
        onChange(toRoleCode(text));
      }}
      onChange={(_e, v) => onChange(v ? toRoleCode(v) : '')}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          required={required}
          error={error}
          helperText={helperText || (value ? `Code: ${value}` : 'Pick or type a role')}
        />
      )}
    />
  );
}

RoleField.propTypes = {
  label: PropTypes.string.isRequired,
  value: PropTypes.string,
  onChange: PropTypes.func.isRequired,
  required: PropTypes.bool,
  error: PropTypes.bool,
  helperText: PropTypes.string,
};
