"""Shared interior decorations. Only fills empty cells and keeps the center aisle clear."""

def decorate(cells, put, width, depth, floor_y, role):
    def is_air(x,y,z):
        return cells.get((x,y,z),('minecraft:air',{}))[0]=='minecraft:air'
    def place(x,y,z,name,props=None):
        if 1<x<width-2 and 1<z<depth-2 and is_air(x,y,z): put(x,y,z,name,props)
    # A free center aisle joins the entrance to the rear and stairs.
    center=width//2
    colors=['orange','brown','red','blue','green','cyan','purple','yellow']
    color=colors[sum(map(ord,role))%len(colors)]
    # Furniture stays against the walls; never replace a bed, work block or stairs.
    for x,z,name in [(width-3,depth-4,'bookshelf'),(width-3,depth-5,'bookshelf'),(3,depth-3,'barrel'),(width-3,5,'crafting_table')]:
        place(x,floor_y,z,name)
    for x,z in [(3,4),(width-4,depth-4)]:
        place(x,floor_y,z,'spruce_stairs',{'facing':'north','half':'bottom','shape':'straight','waterlogged':'false'})
    # Lanterns sit only on placed solid furniture.
    for x,z in [(width-3,depth-4),(3,depth-3)]:
        if not is_air(x,floor_y,z) and cells[(x,floor_y,z)][0] in ('minecraft:bookshelf','minecraft:barrel'):
            place(x,floor_y+1,z,'lantern',{'hanging':'false','waterlogged':'false'})
    # Profession details only if there is free room beside an interior wall.
    details={'builder':'stonecutter','farmer':'hay_block','armorer':'anvil','weaponsmith':'grindstone','toolsmith':'smithing_table','mason':'stonecutter','fletcher':'target','fisherman':'barrel','shepherd':'white_wool','butcher':'smoker','leatherworker':'cauldron','librarian':'bookshelf','cartographer':'cartography_table','cleric':'bookshelf','guard':'target','treasurer':'gold_block'}
    if role in details: place(width-3,floor_y,depth-3,details[role])
    for x in range(3,width-3):
        for z in range(4,depth-3):
            below=cells.get((x,floor_y-1,z),('minecraft:air',{}))[0]
            if x != center and (x+z)%3 != 0 and below in ('minecraft:stone_bricks','minecraft:spruce_planks','minecraft:cracked_stone_bricks','minecraft:mossy_stone_bricks'):
                place(x,floor_y,z,color+'_carpet')
