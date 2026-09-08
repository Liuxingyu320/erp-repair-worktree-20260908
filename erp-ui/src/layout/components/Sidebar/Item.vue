<script>
function resolveIconTone(icon) {
  const name = String(icon || '').toLowerCase()
  if (/(user|people|peoples|role|staff|team|hr)/.test(name)) return 'violet'
  if (/(dept|tree|office|company|shop|store|branch)/.test(name)) return 'sage'
  if (/(goods|product|shopping|cart|inventory|warehouse|purchase|order|money)/.test(name)) return 'amber'
  if (/(warning|notice|bell|audit|log|security|lock|risk)/.test(name)) return 'rose'
  return 'blue'
}

export default {
  name: 'MenuItem',
  functional: true,
  props: {
    icon: {
      type: String,
      default: ''
    },
    title: {
      type: String,
      default: ''
    }
  },
  render(h, context) {
    const { icon, title } = context.props
    const vnodes = []

    if (icon) {
      vnodes.push(
        <svg-icon
          icon-class={icon}
          class={['menu-icon-tone', `menu-icon-tone--${resolveIconTone(icon)}`]}
        />
      )
    }

    if (title) {
      if (title.length > 5) {
        vnodes.push(<span slot='title' title={(title)}>{(title)}</span>)
      } else {
        vnodes.push(<span slot='title'>{(title)}</span>)
      }
    }
    return vnodes
  }
}
</script>
