import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import PhaseChip from './PhaseChip.jsx';

describe('PhaseChip', () => {
  it('shows the readable phase name', () => {
    render(<PhaseChip phase="PROCUREMENT" />);
    expect(screen.getByText('Procurement')).toBeInTheDocument();
  });
});
