import { useRef, useState } from 'react';
import PropTypes from 'prop-types';
import { Button, Chip, IconButton, Link, Stack, Tooltip, Typography } from '@mui/material';
import AttachFileIcon from '@mui/icons-material/AttachFile';
import CloseIcon from '@mui/icons-material/Close';
import { downloadCloseoutDocument, useCloseoutAction, useCloseoutUpload } from '../../api/closeout.js';

/** A chip from a { label, color } map. */
export function MetaChip({ map, value, variant }) {
  const m = map[value] ?? { label: value, color: 'default' };
  return <Chip size="small" label={m.label} color={m.color} variant={variant} />;
}

MetaChip.propTypes = { map: PropTypes.object.isRequired, value: PropTypes.string, variant: PropTypes.string };

/** Attached files with download and (where allowed) remove. */
export function FileList({ projectId, files }) {
  const action = useCloseoutAction(projectId);
  const [error, setError] = useState(null);
  if (!files.length) return null;
  return (
    <Stack spacing={0.25} sx={{ mt: 0.5 }}>
      {files.map((f) => (
        <Stack key={f.id} direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
          <AttachFileIcon fontSize="inherit" color="action" />
          <Link
            component="button"
            type="button"
            variant="body2"
            onClick={() => {
              setError(null);
              downloadCloseoutDocument(projectId, f).catch(setError);
            }}
          >
            {f.fileName}
          </Link>
          {f.canDelete && (
            <Tooltip title="Remove">
              <IconButton
                size="small"
                aria-label={`Remove ${f.fileName}`}
                onClick={() => action.mutate({ path: `/documents/${f.id}`, method: 'DELETE' })}
              >
                <CloseIcon fontSize="inherit" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      ))}
      {(error || action.error) && (
        <Typography variant="caption" color="error">
          {(error || action.error).message}
        </Typography>
      )}
    </Stack>
  );
}

FileList.propTypes = { projectId: PropTypes.string.isRequired, files: PropTypes.array.isRequired };

/** A button that uploads one file straight away. */
export function UploadButton({ projectId, kind, refId, label = 'Attach file', size = 'small' }) {
  const upload = useCloseoutUpload(projectId);
  const input = useRef(null);
  return (
    <>
      <Button size={size} startIcon={<AttachFileIcon />} loading={upload.isPending} onClick={() => input.current?.click()}>
        {label}
      </Button>
      <input
        ref={input}
        type="file"
        hidden
        onChange={(e) => {
          const file = e.target.files?.[0];
          e.target.value = '';
          if (file) upload.mutate({ kind, refId, file });
        }}
      />
      {upload.error && (
        <Typography variant="caption" color="error" sx={{ ml: 1 }}>
          {upload.error.message}
        </Typography>
      )}
    </>
  );
}

UploadButton.propTypes = {
  projectId: PropTypes.string.isRequired,
  kind: PropTypes.oneOf(['EVIDENCE', 'DEFECT', 'HANDOVER']).isRequired,
  refId: PropTypes.string,
  label: PropTypes.string,
  size: PropTypes.string,
};
