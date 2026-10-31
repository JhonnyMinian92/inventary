export default function Field({ label, required = false, wide = false, children, hint }) {
  return <label className={'field' + (wide ? ' wide' : '')}>
    <span>{label}{required && <b aria-hidden="true"> *</b>}</span>
    {children}
    {hint && <small className="field-hint">{hint}</small>}
  </label>;
}
