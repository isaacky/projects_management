import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload } from './client.js';

const base = (projectId) => `/api/v1/projects/${projectId}/schedule`;
export const scheduleKey = (projectId) => ['projects', projectId, 'schedule'];

export function useSchedule(projectId) {
  return useQuery({
    queryKey: scheduleKey(projectId),
    queryFn: ({ signal }) => apiFetch(base(projectId), { signal }),
    enabled: Boolean(projectId),
  });
}

function useScheduleMutation(projectId, fn) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (view) => {
      qc.setQueryData(scheduleKey(projectId), view);
      qc.invalidateQueries({ queryKey: ['projects', projectId, 'history'] });
      qc.invalidateQueries({ queryKey: ['portfolio-health'] });
    },
  });
}

/** body: { startDate, version, activities: [{ key, parentKey, name, durationDays, earliestStart, weight, ownerId, predecessors }] } */
export const useSaveSchedule = (projectId) =>
  useScheduleMutation(projectId, (body) => apiFetch(base(projectId), { method: 'PUT', body }));

export const useBaseline = (projectId) =>
  useScheduleMutation(projectId, (note) => apiFetch(`${base(projectId)}/baseline`, { method: 'POST', body: { note } }));

export const useRecordProgress = (projectId) =>
  useScheduleMutation(projectId, ({ activityId, ...body }) =>
    apiFetch(`${base(projectId)}/activities/${activityId}/progress`, { method: 'PUT', body }),
  );

export const useImportSchedule = (projectId) =>
  useScheduleMutation(projectId, (file) => {
    const form = new FormData();
    form.append('file', file);
    return apiUpload(`${base(projectId)}/import`, form);
  });
