package com.example.bruffwalkingtour

object BruffTourData {
    
    fun getDefaultTour(): WalkingTour {
        return WalkingTour(
            id = "bruff_heritage_trail",
            name = "Bruff Heritage Trail",
            description = "Discover the rich history and beautiful architecture of Bruff town",
            estimatedDurationMinutes = 90,
            difficulty = TourDifficulty.EASY,
            waypoints = listOf(
                TourWaypoint(
                    id = "thomas_fitzgerald_centre",
                    name = "Thomas Fitzgerald Centre",
                    description = "Heritage center housed in a former courthouse from 1926, dedicated to the Kennedy family connection to Bruff.",
                    latitude = 52.478689,
                    longitude = -8.548776,
                    historicalInfo = "This building served as Bruff's courthouse from 1926 and later housed the town library before becoming a heritage center. It commemorates Thomas Fitzgerald, great-grandfather of President John F. Kennedy, who emigrated from Bruff in 1852. The center was officially dedicated by Caroline Kennedy in 2013 and features a hand-painted Fitzgerald family tree mural and JFK exhibitions. A life-size bronze statue of JFK was unveiled here in 2019.",
                    proximityRadius = 25.0,
                    imageUrl = "https://dynamic-media-cdn.tripadvisor.com/media/photo-o/09/cf/42/0b/thomas-fitzgerald-center.jpg"
                ),
                TourWaypoint(
                    id = "bruff_catholic_church",
                    name = "Saints Peter and Paul Catholic Church",
                    description = "Gothic Revival church built 1828-1833 by Rev. Dr. Andrew Ryan and completed by Dean Patrick MacNamara. Features octagonal limestone spire, snecked limestone walls, and exemplifies the emerging confidence of the Irish Catholic Church after Emancipation.",
                    latitude = 52.478558,
                    longitude = -8.548009,
                    historicalInfo = "Commenced in 1828 by Rev. Dr. Andrew Ryan (marked by a plaque reading '1828 Andra. Ryan V.G.P.P.') and completed in 1833 by his successor Dean Patrick MacNamara. This freestanding Early English Gothic Revival church features a five-bay nave, three-stage broach tower with octagonal limestone spire, timber hammerbeam roof, and carved timber gallery. The snecked limestone walls with limestone buttresses and trefoil-headed windows reflect the emerging confidence of Irish Catholic Church architecture in the post-Emancipation era. Interior highlights include a marble reredos at the altar and the Sacred Heart side altar donated in 1950.",
                    proximityRadius = 20.0,
                    imageUrl = "https://freepages.rootsweb.com/~irishchurches/genealogy/RC%20Churches/Bruff.jpg"
                ),
                TourWaypoint(
                    id = "sean_wall_monument",
                    name = "Sean Wall Monument",
                    description = "Portland stone memorial designed by sculptor Albert Power and carved by Leo Broe. Depicts an IRA Volunteer in belted trench coat, commemorating Brigadier Sean Wall and the East Limerick Brigade. Unveiled by President Sean T. O'Kelly in 1952 after 25 years of fundraising.",
                    latitude = 52.477636,
                    longitude = -8.547905,
                    historicalInfo = "Commemorates Brigadier Sean Wall (1888-1921), Officer Commanding East Limerick Brigade IRA, who was killed in action at Annacarty, Co. Tipperary on 6th May 1921. Originally designed by renowned sculptor Albert Power in 1931, the monument took over 25 years to fund - requiring £2,500 raised through thousands of contributions. Carved in Portland stone by Leo Broe in the early 1950s, it depicts an IRA Volunteer in belted trench coat and gaiters, carrying a revolver with cap peak turned backward. The memorial committee, formed in 1944, remarkably included men from both sides of the Civil War. Officially unveiled by President Sean T. O'Kelly in 1952, it stands as a testament to the War of Independence sacrifice and the community's long dedication to honoring their fallen heroes.",
                    proximityRadius = 15.0,
                    imageUrl = "https://www.buildingsofireland.ie/building-images-iiif/niah/images/survey_specific/original/21803004_3.jpg"
                ),
                TourWaypoint(
                    id = "bruff_gaa_grounds",
                    name = "Bruff GAA Grounds",
                    description = "Home to Bruff GAA club, one of Ireland's oldest GAA clubs founded in 1887. The grounds host hurling and Gaelic football matches and have witnessed over 135 years of competitive Irish sport, including county championship games.",
                    latitude = 52.476002,
                    longitude = -8.541206,
                    historicalInfo = "Founded in 1887, Bruff GAA club represents one of the oldest Gaelic Athletic Association clubs in Ireland, established in the same year as the first All-Ireland Football Championship (which Limerick won in 1887). The club fields teams in both hurling and Gaelic football, with notable achievements including the Limerick Intermediate Hurling Championship victories in 1989, 2008, and promotion to Premier Intermediate after winning in 2014. The grounds regularly host county-level championship matches, serving as a focal point for traditional Irish sport. Interestingly, Bruff has connections to GAA history through Liam MacCarthy (namesake of the All-Ireland hurling trophy), whose mother Brigid Dineen was from Crawford Lane, Bruff. The club continues its 135+ year tradition as a cornerstone of the community's sporting and cultural identity.",
                    proximityRadius = 30.0,
                    imageUrl = "https://www.limerickpost.ie/site/wp-content/uploads/2023/12/Screenshot-2023-12-22-at-12.08.32-696x349.png"
                )
            )
        )
    }
}