import { useState } from 'react';
import { Card, CardContent, MenuItem, Skeleton, Stack, TextField, Typography } from '@mui/material';
import PageHeader from '../../components/PageHeader.jsx';
import ErrorAlert from '../../components/ErrorAlert.jsx';
import { useLessons } from '../../api/closeout.js';
import { LESSON_CATEGORY, LESSON_KIND } from '../../domain/closeout.js';
import { LessonItem } from './LessonsSection.jsx';

export default function LessonsPage() {
  const [q, setQ] = useState('');
  const [category, setCategory] = useState('');
  const [kind, setKind] = useState('');
  const { data, isLoading, error } = useLessons({ q: q.trim(), category, kind });
  const awaiting = (data ?? []).filter((l) => l.status === 'SUBMITTED').length;
  return (
    <>
      <PageHeader
        title="Lessons learned"
        subtitle="What went well and what to improve, from every project. The PMO adopts lessons into procedures and templates."
      />
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
        <TextField size="small" label="Search" value={q} onChange={(e) => setQ(e.target.value)} sx={{ minWidth: 240 }} />
        <TextField select size="small" label="Category" value={category} onChange={(e) => setCategory(e.target.value)} sx={{ minWidth: 170 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(LESSON_CATEGORY).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label="Kind" value={kind} onChange={(e) => setKind(e.target.value)} sx={{ minWidth: 150 }}>
          <MenuItem value="">All</MenuItem>
          {Object.entries(LESSON_KIND).map(([k, v]) => (
            <MenuItem key={k} value={k}>
              {v.label}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      <ErrorAlert error={error} title="Could not load lessons" />
      <Card>
        <CardContent>
          {isLoading || !data ? (
            isLoading && <Skeleton variant="rounded" height={200} />
          ) : (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                {data.length} lesson{data.length === 1 ? '' : 's'}
                {awaiting ? ` · ${awaiting} awaiting PMO review` : ''}
              </Typography>
              <Stack spacing={1.5}>
                {data.map((l) => (
                  <LessonItem key={l.id} lesson={l} showProject />
                ))}
              </Stack>
              {data.length === 0 && (
                <Typography variant="body2" color="text.secondary">
                  No lessons match.
                </Typography>
              )}
            </>
          )}
        </CardContent>
      </Card>
    </>
  );
}
