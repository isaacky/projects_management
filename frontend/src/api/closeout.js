import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiUpload, downloadFile } from './client.js';
import { projectKeys } from './projects.js';

const projectBase = (projectId) => `/api/v1/projects/${projectId}`;

export const closeoutKeys = {
  detail: (projectId) => ['closeout', projectId],
  lessons: (filter) => ['lessons', filter ?? {}],
  template: ['closeout-checklist'],
};

export function useCloseout(projectId) {
  return useQuery({
    queryKey: closeoutKeys.detail(projectId),
    queryFn: ({ signal }) => apiFetch(`${projectBase(projectId)}/closeout`, { signal }),
    enabled: Boolean(projectId),
  });
}

function useRefresh(projectId) {
  const qc = useQueryClient();
  return (view) => {
    if (view?.projectId) qc.setQueryData(closeoutKeys.detail(projectId), view);
    else qc.invalidateQueries({ queryKey: closeoutKeys.detail(projectId) });
    qc.invalidateQueries({ queryKey: ['lessons'] });
    qc.invalidateQueries({ queryKey: ['notifications'] });
    // Closing changes the project's status; accepting the handover opens gate G4.
    qc.invalidateQueries({ queryKey: projectKeys.all });
  };
}

/** Any closeout change; every endpoint returns the updated closeout. vars: { path, method, body } */
export function useCloseoutAction(projectId) {
  const refresh = useRefresh(projectId);
  return useMutation({
    mutationFn: ({ path, method = 'POST', body }) =>
      apiFetch(`${projectBase(projectId)}/closeout${path}`, {
        method,
        body: method === 'DELETE' ? undefined : (body ?? {}),
      }),
    onSuccess: refresh,
  });
}

/** vars: { kind: 'EVIDENCE' | 'DEFECT' | 'HANDOVER', refId, file } */
export function useCloseoutUpload(projectId) {
  const refresh = useRefresh(projectId);
  return useMutation({
    mutationFn: ({ kind, refId, file }) => {
      const form = new FormData();
      form.append('file', file);
      const qs = new URLSearchParams({ kind });
      if (refId) qs.set('refId', refId);
      return apiUpload(`${projectBase(projectId)}/closeout/documents?${qs}`, form);
    },
    onSuccess: refresh,
  });
}

export function downloadCloseoutDocument(projectId, doc) {
  return downloadFile(`${projectBase(projectId)}/closeout/documents/${doc.id}`, doc.fileName);
}

/** filter: { q, category, kind } */
export function useLessons(filter) {
  const qs = new URLSearchParams(Object.entries(filter ?? {}).filter(([, v]) => v)).toString();
  return useQuery({
    queryKey: closeoutKeys.lessons(filter),
    queryFn: ({ signal }) => apiFetch(`/api/v1/lessons${qs ? `?${qs}` : ''}`, { signal }),
  });
}

/** vars: { projectId, lessonId?, body } — adds or edits a lesson on a project. */
export function useSaveLesson() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ projectId, lessonId, body }) =>
      apiFetch(`${projectBase(projectId)}/lessons${lessonId ? `/${lessonId}` : ''}`, {
        method: lessonId ? 'PUT' : 'POST',
        body,
      }),
    onSuccess: (lesson) => {
      qc.invalidateQueries({ queryKey: ['lessons'] });
      qc.invalidateQueries({ queryKey: closeoutKeys.detail(lesson.projectId) });
    },
  });
}

/** vars: { lessonId, status: 'ADOPTED' | 'NOTED', note } */
export function useReviewLesson() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ lessonId, status, note }) =>
      apiFetch(`/api/v1/lessons/${lessonId}/review`, { method: 'POST', body: { status, note } }),
    onSuccess: (lesson) => {
      qc.invalidateQueries({ queryKey: ['lessons'] });
      qc.invalidateQueries({ queryKey: closeoutKeys.detail(lesson.projectId) });
    },
  });
}

export function useChecklistTemplate() {
  return useQuery({
    queryKey: closeoutKeys.template,
    queryFn: ({ signal }) => apiFetch('/api/v1/settings/closeout-checklist', { signal }),
  });
}

export function useSaveChecklistTemplate() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (items) => apiFetch('/api/v1/settings/closeout-checklist', { method: 'PUT', body: { items } }),
    onSuccess: (view) => qc.setQueryData(closeoutKeys.template, view),
  });
}
