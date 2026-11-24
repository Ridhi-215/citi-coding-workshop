import { alpha, createTheme } from '@mui/material';

/*
 * ACME Tracker visual theme. All colors, type, shape and component styling live here so the
 * pages stay free of one-off styles. Text/background pairs were checked for WCAG AA (>= 4.5:1).
 */

export const BRAND = {
  teal: '#0f5f5a', // primary: 7.0:1 on the page background, 7.5:1 on white
  tealDark: '#0a4642',
  tealSoft: '#e6f1ef',
  coral: '#e0795f', // accent, used sparingly and never for small text
  coralSoft: '#fde9e3',
  page: '#faf7f2', // warm off-white
  sidebar: '#f2eee6',
  ink: '#1e2a2a',
  inkMuted: '#5c6766', // 5.5:1 on the page background
  line: '#e7e2d9',
};

/** Pastel background + dark text for chips, per MUI color. */
export const SOFT = {
  default: { bg: '#eceeec', fg: '#3d4a49' },
  primary: { bg: '#dcefeb', fg: '#0b5d57' },
  secondary: { bg: BRAND.coralSoft, fg: '#9a4a33' },
  success: { bg: '#e3f3e6', fg: '#1f6b35' },
  warning: { bg: '#fdf0d9', fg: '#8a4b00' },
  error: { bg: '#fbe5e2', fg: '#a3302a' },
  info: { bg: '#e4eafa', fg: '#2f4ea0' },
};

const EASE = 'cubic-bezier(0.2, 0, 0, 1)';
const transition = (props) => props.map((p) => `${p} 180ms ${EASE}`).join(', ');
const NO_MOTION = '@media (prefers-reduced-motion: reduce)';
const MOTION = '@media (prefers-reduced-motion: no-preference)';

const cardShadow = '0 1px 2px rgba(30, 42, 42, 0.04), 0 2px 8px rgba(30, 42, 42, 0.05)';
const cardShadowHover = '0 4px 10px rgba(30, 42, 42, 0.06), 0 10px 24px rgba(30, 42, 42, 0.08)';

const heading = { fontFamily: '"Plus Jakarta Sans", Inter, system-ui, sans-serif', color: BRAND.ink };

const theme = createTheme({
  palette: {
    primary: { main: BRAND.teal, dark: BRAND.tealDark, light: '#3f8a84', contrastText: '#ffffff' },
    secondary: { main: BRAND.coral, contrastText: BRAND.ink },
    success: { main: '#2e7d4f' },
    warning: { main: '#b26a00' },
    error: { main: '#b3372f' },
    info: { main: '#2f5bb5' },
    background: { default: BRAND.page, paper: '#ffffff' },
    text: { primary: BRAND.ink, secondary: BRAND.inkMuted },
    divider: BRAND.line,
    action: { hover: alpha(BRAND.teal, 0.05), selected: alpha(BRAND.teal, 0.1) },
  },
  shape: { borderRadius: 12 },
  typography: {
    fontFamily: '"Plus Jakarta Sans", Inter, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif',
    h3: { ...heading, fontWeight: 800, letterSpacing: '-0.02em' },
    h4: { ...heading, fontWeight: 800, letterSpacing: '-0.02em', fontVariantNumeric: 'tabular-nums' },
    h5: { ...heading, fontWeight: 800, fontSize: '1.6rem', letterSpacing: '-0.015em' },
    h6: { ...heading, fontWeight: 700, letterSpacing: '-0.01em' },
    subtitle1: { fontWeight: 700, letterSpacing: '-0.005em' },
    body2: { lineHeight: 1.55 },
    overline: { display: 'block', fontWeight: 700, letterSpacing: '0.12em' },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiCssBaseline: {
      styleOverrides: {
        body: { WebkitFontSmoothing: 'antialiased' },
        [NO_MOTION]: {
          '*, *::before, *::after': {
            transitionDuration: '0.01ms !important',
            animationDuration: '0.01ms !important',
            animationIterationCount: '1 !important',
          },
        },
      },
    },
    MuiPaper: {
      styleOverrides: {
        outlined: { borderColor: BRAND.line, boxShadow: cardShadow },
        rounded: { borderRadius: 12 },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          borderRadius: 12,
          border: `1px solid ${BRAND.line}`,
          boxShadow: cardShadow,
          transition: transition(['box-shadow', 'transform']),
          [MOTION]: { '&:hover': { transform: 'translateY(-2px)' } },
          '&:hover': { boxShadow: cardShadowHover },
        },
      },
    },
    MuiCardContent: { styleOverrides: { root: { padding: 20, '&:last-child': { paddingBottom: 20 } } } },
    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        root: {
          borderRadius: 10,
          transition: transition(['background-color', 'box-shadow', 'border-color', 'color', 'transform']),
          '&:active': { transform: 'scale(0.98)' },
        },
        contained: { '&:hover': { boxShadow: `0 4px 12px ${alpha(BRAND.teal, 0.25)}` } },
      },
    },
    MuiIconButton: {
      styleOverrides: { root: { transition: transition(['background-color', 'color', 'transform']), '&:active': { transform: 'scale(0.94)' } } },
    },
    MuiChip: {
      styleOverrides: {
        root: ({ ownerState }) => {
          const soft = SOFT[ownerState.color] || SOFT.default;
          const base = { fontWeight: 600, borderRadius: 8 };
          if (ownerState.variant === 'outlined') {
            // At-risk / overdue / over-allocated flags (error) get the pastel fill as well.
            return {
              ...base,
              color: soft.fg,
              borderColor: alpha(soft.fg, 0.35),
              backgroundColor: ownerState.color === 'error' ? soft.bg : 'transparent',
              '& .MuiChip-icon': { color: soft.fg },
            };
          }
          return { ...base, color: soft.fg, backgroundColor: soft.bg, '& .MuiChip-icon': { color: soft.fg } };
        },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: { backgroundColor: alpha(BRAND.page, 0.85), backdropFilter: 'blur(8px)', borderColor: BRAND.line },
      },
    },
    MuiDrawer: {
      styleOverrides: { paper: { backgroundColor: BRAND.sidebar, borderColor: BRAND.line } },
    },
    MuiListItemButton: {
      styleOverrides: {
        root: {
          position: 'relative',
          margin: '2px 12px',
          borderRadius: 10,
          color: BRAND.inkMuted,
          transition: transition(['background-color', 'color', 'padding-left']),
          '& .MuiListItemIcon-root': { minWidth: 38, color: 'inherit' },
          '& .MuiListItemText-primary': { fontWeight: 600, fontSize: '0.92rem' },
          '&:hover': { backgroundColor: alpha(BRAND.teal, 0.07), color: BRAND.ink },
          [MOTION]: { '&:hover': { paddingLeft: 20 } },
          '&.active': {
            backgroundColor: '#ffffff',
            color: BRAND.teal,
            boxShadow: cardShadow,
            '&::before': {
              content: '""',
              position: 'absolute',
              left: -12,
              top: 8,
              bottom: 8,
              width: 4,
              borderRadius: '0 4px 4px 0',
              backgroundColor: BRAND.coral,
            },
          },
        },
      },
    },
    MuiTableHead: {
      styleOverrides: {
        root: {
          '& .MuiTableCell-head': {
            backgroundColor: '#f6f3ee',
            color: BRAND.inkMuted,
            fontWeight: 700,
            fontSize: '0.75rem',
            textTransform: 'uppercase',
            letterSpacing: '0.06em',
          },
        },
      },
    },
    MuiTableRow: {
      styleOverrides: {
        root: {
          transition: transition(['background-color']),
          '.MuiTableBody-root &:hover': { backgroundColor: alpha(BRAND.teal, 0.04) },
        },
      },
    },
    MuiTableCell: { styleOverrides: { root: { borderColor: BRAND.line } } },
    MuiOutlinedInput: {
      styleOverrides: {
        root: {
          backgroundColor: '#ffffff',
          borderRadius: 10,
          transition: transition(['box-shadow']),
          '&.Mui-focused': { boxShadow: `0 0 0 3px ${alpha(BRAND.teal, 0.15)}` },
        },
      },
    },
    MuiDialog: { styleOverrides: { paper: { borderRadius: 16 } } },
    MuiDialogTitle: { styleOverrides: { root: { ...heading, fontWeight: 700 } } },
    MuiLinearProgress: { styleOverrides: { root: { borderRadius: 4, backgroundColor: '#ece7de' }, bar: { borderRadius: 4 } } },
    MuiTooltip: { styleOverrides: { tooltip: { backgroundColor: BRAND.ink, fontSize: '0.75rem' } } },
    MuiAvatar: { styleOverrides: { root: { backgroundColor: BRAND.coralSoft, color: '#9a4a33', fontWeight: 700 } } },
    MuiLink: { styleOverrides: { root: { fontWeight: 600 } } },
  },
});

export default theme;
