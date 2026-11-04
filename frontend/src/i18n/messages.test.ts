import { describe, expect, it } from 'vitest'
import { dictionaries, en, LANGUAGES, si, ta } from './messages'

const SINHALA = /[඀-෿]/
const TAMIL = /[஀-௿]/

describe('translations', () => {
  it('define the same keys in all three languages', () => {
    expect(Object.keys(si).sort()).toEqual(Object.keys(en).sort())
    expect(Object.keys(ta).sort()).toEqual(Object.keys(en).sort())
  })

  it('are really in Sinhala and Tamil, never English left in place', () => {
    for (const key of Object.keys(en) as (keyof typeof en)[]) {
      expect(si[key], key).toMatch(SINHALA)
      expect(ta[key], key).toMatch(TAMIL)
    }
  })

  it('keep every risk label distinct in every language (no two levels read the same)', () => {
    for (const lang of Object.values(dictionaries)) {
      const labels = [lang['risk.low'], lang['risk.medium'], lang['risk.high'], lang['risk.critical']]
      expect(new Set(labels).size).toBe(4)
    }
  })

  it('name each language in itself', () => {
    expect(LANGUAGES.map((l) => l.name)).toEqual(['English', 'සිංහල', 'தமிழ்'])
  })
})
