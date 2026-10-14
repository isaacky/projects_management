import PropTypes from 'prop-types';
import { Box, Button, Chip, Stack, Typography } from '@mui/material';
import AttachFileOutlined from '@mui/icons-material/AttachFileOutlined';
import { formatBytes } from '../../domain/tenders.js';

const MAX_FILE = 200 * 1024 * 1024;

/** Picks several files; shows them as removable chips. */
export default function FilePicker({ label, files, onChange, helperText }) {
  const add = (e) => {
    const picked = Array.from(e.target.files ?? []);
    e.target.value = '';
    onChange([...files, ...picked]);
  };
  const tooBig = files.filter((f) => f.size > MAX_FILE);
  return (
    <Box>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
        <Button component="label" variant="outlined" size="small" startIcon={<AttachFileOutlined />}>
          {label}
          <input hidden type="file" multiple onChange={add} />
        </Button>
        {helperText && (
          <Typography variant="caption" color="text.secondary">
            {helperText}
          </Typography>
        )}
      </Stack>
      {files.length > 0 && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 1 }}>
          {files.map((f, i) => (
            <Chip
              key={`${f.name}-${i}`}
              label={`${f.name} (${formatBytes(f.size)})`}
              color={f.size > MAX_FILE ? 'error' : 'default'}
              onDelete={() => onChange(files.filter((_x, j) => j !== i))}
            />
          ))}
        </Stack>
      )}
      {tooBig.length > 0 && (
        <Typography variant="caption" color="error">
          Files over 200 MB cannot be uploaded.
        </Typography>
      )}
    </Box>
  );
}

FilePicker.propTypes = {
  label: PropTypes.string.isRequired,
  files: PropTypes.arrayOf(PropTypes.object).isRequired,
  onChange: PropTypes.func.isRequired,
  helperText: PropTypes.string,
};

export const filesTooBig = (lists) => lists.flat().some((f) => f.size > MAX_FILE);
