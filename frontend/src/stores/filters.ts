import { defineStore } from 'pinia'

export const useFiltersStore = defineStore('filters', {
  state: () => ({
    typeSlug: null as string | null,
    city: '',
    fromDate: '' as string | null,
    toDate: '' as string | null,
    joinable: false
  }),
  actions: {
    setActivityType(slug: string | null) {
      this.typeSlug = slug
    },
    setCity(city: string) {
      this.city = city
    },
    setFromDate(date: string | null) {
      this.fromDate = date
    },
    reset() {
      this.typeSlug = null
      this.city = ''
      this.fromDate = null
      this.toDate = null
      this.joinable = false
    }
  }
})
