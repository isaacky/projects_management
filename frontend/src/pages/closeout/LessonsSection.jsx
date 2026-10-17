import { useState } from 'react';
import PropTypes from 'prop-types';
import { Link as RouterLink } from 'react-router';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Link,
  MenuItem,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import { useReviewLesson, useSaveLesson } from '../../api/closeout.js';
import { LESSON_CATEGORY, LESSON_KIND, LESSON_STATUS } from '../../domain/closeout.js';
import { PHASES, phaseLabel } from '../../domain/lifecycle.js';
import { formatDateTime } from '../../domain/tenders.js';
import { MetaChip } from './CloseoutParts.jsx';

/** lesson: the lesson being edited; draft: starting values for a new one (e.g. from a review finding). */
export function LessonDialog({ projectId, lesson, draft, onClose }) {
  const save = useSaveLesson();
  const start = lesson ?? draft;
  const [f, setF] = useState({
    kind: start?.kind ?? 'IMPROVE',
    category: start?.category ?? 'PROCUREMENT',
    phase: start?.phase ?? '',
    title: start?.title ?? '',
    whatHappened: start?.whatHappened ?? '',
    recommendation: start?.recommendation ?? '',
  });
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{lesson ? 'Edit lesson' : 'Record a lesson learned'}</DialogTitle>
      <DialogContent>
        {save.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {save.error.message}
          </Alert>
        )}
        <Grid container spacing={2} sx={{ mt: 0.5 }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <ToggleButtonGroup exclusive size="small" value={f.kind} onChange={(_e, v) => v && setF((s) => ({ ...s, kind: v }))}>
              <ToggleButton value="WENT_WELL">Went well</ToggleButton>
              <ToggleButton value="IMPROVE">To improve</ToggleButton>
            </ToggleButtonGroup>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <TextField select label="Category" value={f.category} onChange={set('category')} fullWidth>
              {Object.entries(LESSON_CATEGORY).map(([k, v]) => (
                <MenuItem key={k} value={k}>
                  {v}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={{ xs: 6, md: 4 }}>
            <TextField select label="Phase" value={f.phase} onChange={set('phase')} fullWidth>
              <MenuItem value="">The current phase</MenuItem>
              {PHASES.map((p) => (
                <MenuItem key={p.key} value={p.key}>
                  {p.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>
          <Grid size={12}>
            <TextField label="Lesson" value={f.title} onChange={set('title')} required fullWidth slotProps={{ htmlInput: { maxLength: 200 } }} />
          </Grid>
          <Grid size={12}>
            <TextField
              label="What happened"
              value={f.whatHappened}
              onChange={set('whatHappened')}
              required
              multiline
              minRows={3}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 4000 } }}
            />
          </Grid>
          <Grid size={12}>
            <TextField
              label="Recommendation"
              value={f.recommendation}
              onChange={set('recommendation')}
              helperText="What future projects should do (or keep doing)"
              multiline
              minRows={2}
              fullWidth
              slotProps={{ htmlInput: { maxLength: 4000 } }}
            />
          </Grid>
        </Grid>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={!f.title.trim() || !f.whatHappened.trim()}
          loading={save.isPending}
          onClick={() =>
            save.mutate(
              {
                projectId,
                lessonId: lesson?.id,
                body: {
                  kind: f.kind,
                  category: f.category,
                  phase: f.phase || null,
                  title: f.title.trim(),
                  whatHappened: f.whatHappened.trim(),
                  recommendation: f.recommendation.trim() || null,
                },
              },
              { onSuccess: onClose },
            )
          }
        >
          Save
        </Button>
      </DialogActions>
    </Dialog>
  );
}

LessonDialog.propTypes = {
  projectId: PropTypes.string.isRequired,
  lesson: PropTypes.object,
  draft: PropTypes.object,
  onClose: PropTypes.func.isRequired,
};

function ReviewDialog({ lesson, onClose }) {
  const review = useReviewLesson();
  const [status, setStatus] = useState('ADOPTED');
  const [note, setNote] = useState('');
  const needsNote = status === 'ADOPTED' && !note.trim();
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Review: {lesson.title}</DialogTitle>
      <DialogContent>
        {review.error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {review.error.message}
          </Alert>
        )}
        <ToggleButtonGroup exclusive size="small" value={status} onChange={(_e, v) => v && setStatus(v)} sx={{ mb: 2 }}>
          <ToggleButton value="ADOPTED">Adopt</ToggleButton>
          <ToggleButton value="NOTED">Note</ToggleButton>
        </ToggleButtonGroup>
        <TextField
          label={status === 'ADOPTED' ? 'What will change' : 'Note (optional)'}
          helperText={status === 'ADOPTED' ? 'e.g. the procedure, template or checklist that will be updated' : ' '}
          value={note}
          onChange={(e) => setNote(e.target.value)}
          required={status === 'ADOPTED'}
          multiline
          minRows={2}
          fullWidth
          slotProps={{ htmlInput: { maxLength: 2000 } }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Cancel</Button>
        <Button
          variant="contained"
          disabled={needsNote}
          loading={review.isPending}
          onClick={() => review.mutate({ lessonId: lesson.id, status, note: note.trim() || null }, { onSuccess: onClose })}
        >
          Save review
        </Button>
      </DialogActions>
    </Dialog>
  );
}

ReviewDialog.propTypes = { lesson: PropTypes.object.isRequired, onClose: PropTypes.func.isRequired };

/** One lesson, with edit and review actions; showProject for the library. */
export function LessonItem({ lesson, showProject = false }) {
  const [editing, setEditing] = useState(false);
  const [reviewing, setReviewing] = useState(false);
  return (
    <Box sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
        <Box sx={{ minWidth: 0 }}>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <MetaChip map={LESSON_KIND} value={lesson.kind} variant="outlined" />
            <Typography variant="body1" sx={{ fontWeight: 600 }}>
              {lesson.title}
            </Typography>
            <MetaChip map={LESSON_STATUS} value={lesson.status} />
          </Stack>
          <Typography variant="body2" color="text.secondary">
            {LESSON_CATEGORY[lesson.category]}
            {lesson.phase ? ` · ${phaseLabel(lesson.phase)}` : ''}
            {showProject && (
              <>
                {' · '}
                <Link component={RouterLink} to={`/projects/${lesson.projectId}?tab=closeout`}>
                  {lesson.projectCode}
                </Link>
              </>
            )}
            {` · ${lesson.createdBy?.name}, ${formatDateTime(lesson.createdAt)}`}
          </Typography>
          <Typography variant="body2" sx={{ mt: 0.5 }}>
            {lesson.whatHappened}
          </Typography>
          {lesson.recommendation && (
            <Typography variant="body2" sx={{ mt: 0.5 }}>
              <strong>Recommendation:</strong> {lesson.recommendation}
            </Typography>
          )}
          {lesson.reviewNote && (
            <Typography variant="body2" color="success.main" sx={{ mt: 0.5 }}>
              PMO ({lesson.reviewedBy?.name}): {lesson.reviewNote}
            </Typography>
          )}
        </Box>
        <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, alignItems: 'flex-start' }}>
          {lesson.canEdit && (
            <Button size="small" onClick={() => setEditing(true)}>
              Edit
            </Button>
          )}
          {lesson.canReview && (
            <Button size="small" variant="outlined" onClick={() => setReviewing(true)}>
              Review
            </Button>
          )}
        </Stack>
      </Stack>
      {editing && <LessonDialog projectId={lesson.projectId} lesson={lesson} onClose={() => setEditing(false)} />}
      {reviewing && <ReviewDialog lesson={lesson} onClose={() => setReviewing(false)} />}
    </Box>
  );
}

LessonItem.propTypes = { lesson: PropTypes.object.isRequired, showProject: PropTypes.bool };

export default function LessonsSection({ closeout }) {
  const [adding, setAdding] = useState(false);
  return (
    <Card sx={{ mb: 3 }}>
      <CardContent>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
          <Box>
            <Typography variant="h6">Lessons learned</Typography>
            <Typography variant="body2" color="text.secondary">
              Anyone on the project can record one at any time. The PMO reviews them; all projects&apos; lessons are in
              the <Link component={RouterLink} to="/lessons">lessons library</Link>.
            </Typography>
          </Box>
          {closeout.permissions.canAddLesson && (
            <Button startIcon={<AddIcon />} onClick={() => setAdding(true)} sx={{ flexShrink: 0 }}>
              Add lesson
            </Button>
          )}
        </Stack>
        <Stack spacing={1.5}>
          {closeout.lessons.map((l) => (
            <LessonItem key={l.id} lesson={l} />
          ))}
        </Stack>
        {closeout.lessons.length === 0 && (
          <Typography variant="body2" color="text.secondary">
            None yet.
          </Typography>
        )}
      </CardContent>
      {adding && <LessonDialog projectId={closeout.projectId} onClose={() => setAdding(false)} />}
    </Card>
  );
}

LessonsSection.propTypes = { closeout: PropTypes.object.isRequired };
