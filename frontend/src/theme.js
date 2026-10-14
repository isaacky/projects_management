import { createTheme } from '@mui/material/styles';

// One theme for the whole app. Tenants can later override palette.primary
// (for example with their own brand colour) without touching components.
const theme = createTheme({
  cssVariables: true,
  colorSchemes: {
    light: {
      palette: {
        primary: { main: '#1F4E79' },
        secondary: { main: '#C17C00' },
        background: { default: '#F5F7FA', paper: '#FFFFFF' },
      },
    },
    dark: {
      palette: {
        primary: { main: '#7FB2E5' },
        secondary: { main: '#F0B44C' },
      },
    },
  },
  shape: { borderRadius: 8 },
  typography: {
    fontFamily: '"Inter", "Segoe UI", Roboto, Helvetica, Arial, sans-serif',
    h4: { fontWeight: 700, letterSpacing: '-0.01em' },
    h5: { fontWeight: 600 },
    h6: { fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiPaper: { defaultProps: { elevation: 0 } },
    MuiCard: {
      styleOverrides: {
        root: ({ theme: t }) => ({ border: `1px solid ${t.vars.palette.divider}` }),
      },
    },
    MuiButton: { defaultProps: { disableElevation: true } },
  },
});

export default theme;
